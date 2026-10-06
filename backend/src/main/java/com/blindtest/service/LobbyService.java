package com.blindtest.service;

import com.blindtest.error.ApiErrorCode;
import com.blindtest.model.dto.CreateLobbyRequest;
import com.blindtest.model.dto.GuestJoinRequest;
import com.blindtest.model.dto.GuestJoinResponse;
import com.blindtest.model.dto.LobbyParticipantResponse;
import com.blindtest.model.dto.LobbyResponse;
import com.blindtest.model.dto.SessionConnectionInstructions;
import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.lobby.AudioMode;
import com.blindtest.model.lobby.LobbyCode;
import com.blindtest.model.lobby.LobbyParticipant;
import com.blindtest.model.lobby.LobbySnapshot;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.service.port.LobbyCodeGenerator;
import com.blindtest.service.port.SessionIdentityGenerator;
import com.blindtest.store.LobbyStore;
import com.blindtest.store.SessionCreateOutcome;
import com.blindtest.store.SessionStore;
import com.blindtest.websocket.SessionEventPublisher;
import com.blindtest.websocket.protocol.PublicSessionEvent;
import com.blindtest.websocket.protocol.PublicSessionEventPayload;
import com.blindtest.websocket.protocol.PublicSessionEventType;
import com.blindtest.websocket.protocol.SessionEventMetadata;
import com.blindtest.websocket.protocol.SessionProtocol;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Multiplayer lobby lifecycle from creation to start (ADR-0019, MP-02), including guests who
 * join with a nickname instead of an account (ADR-0020, MP-08). Membership changes go
 * through REST and are broadcast as {@code LOBBY_UPDATED} on the session's public topic; start
 * snapshots tracks and creates a {@code CREATED} game session under the lobby's session ID.
 */
@Service
public class LobbyService {

    private static final int MAXIMUM_ID_ATTEMPTS = 20;
    private static final int LOCK_STRIPES = 64;

    private final GameSetupService gameSetupService;
    private final LobbyStore lobbyStore;
    private final SessionStore sessionStore;
    private final SessionIdentityGenerator identityGenerator;
    private final LobbyCodeGenerator codeGenerator;
    private final SessionExpirationPolicy expirationPolicy;
    private final SessionEventPublisher eventPublisher;
    private final GuestAdmission guestAdmission;
    private final Object[] locks = new Object[LOCK_STRIPES];

    /** Wires game setup, lobby and session state, identities, time, events, and guest checks. */
    public LobbyService(
            GameSetupService gameSetupService,
            LobbyStore lobbyStore,
            SessionStore sessionStore,
            SessionIdentityGenerator identityGenerator,
            LobbyCodeGenerator codeGenerator,
            SessionExpirationPolicy expirationPolicy,
            SessionEventPublisher eventPublisher,
            GuestAdmission guestAdmission) {
        this.gameSetupService = gameSetupService;
        this.lobbyStore = lobbyStore;
        this.sessionStore = sessionStore;
        this.identityGenerator = identityGenerator;
        this.codeGenerator = codeGenerator;
        this.expirationPolicy = expirationPolicy;
        this.eventPublisher = eventPublisher;
        this.guestAdmission = guestAdmission;
        Arrays.setAll(locks, ignored -> new Object());
    }

    /** Validates the configuration against the catalogue now and opens a lobby hosted by the caller. */
    public LobbyResponse create(UUID accountId, CreateLobbyRequest request) {
        UserEntity host = gameSetupService.requireVerifiedPlayer(accountId);
        RequestedGameConfiguration requested = gameSetupService.toRequestedConfiguration(
                request == null ? null : request.gameRequest());
        AudioMode audioMode = request == null || request.audioMode() == null
                ? AudioMode.EVERYONE : request.audioMode();
        // Fail early on an unusable configuration; tracks are selected again at start.
        gameSetupService.prepare(requested);
        LobbyParticipant member = newMember(host);
        Instant now = expirationPolicy.instant();
        for (int attempt = 0; attempt < MAXIMUM_ID_ATTEMPTS; attempt++) {
            LobbySnapshot lobby = LobbySnapshot.open(
                    new SessionId(identityGenerator.nextId()),
                    codeGenerator.next(),
                    member,
                    requested,
                    audioMode,
                    now,
                    expiresAt(now));
            if (lobbyStore.create(lobby, now)) {
                return response(lobby, member.participantId());
            }
        }
        throw new IllegalStateException("could not allocate a unique lobby code");
    }

    /**
     * Returns the lobby to one of its members; anyone else gets the not-found response. Like
     * every member operation below, the caller is an account ID or a guest's participant ID.
     */
    public LobbyResponse get(UUID callerId, String rawCode) {
        LobbySnapshot lobby = requireLobby(rawCode);
        LobbyParticipant member = requireMember(lobby, callerId);
        return response(lobby, member.participantId());
    }

    /** Adds the caller to the lobby, or returns the current state when already a member. */
    public LobbyResponse join(UUID accountId, String rawCode) {
        UserEntity user = gameSetupService.requireVerifiedPlayer(accountId);
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            Optional<LobbyParticipant> existing = lobby.memberFor(accountId);
            if (existing.isPresent()) {
                return response(lobby, existing.get().participantId());
            }
            if (lobby.isFull()) {
                throw failure(ApiErrorCode.LOBBY_FULL);
            }
            LobbyParticipant member = newMember(user);
            Instant now = expirationPolicy.instant();
            LobbySnapshot joined = replace(lobby, lobby.join(member, now, expiresAt(now)), now);
            return response(joined, member.participantId());
        }
    }

    /**
     * Admits a guest without an account (ADR-0020) and returns a token scoped to this lobby's
     * session. Rate limits apply before Turnstile is verified, and the code lookup fails the same
     * way for unknown, expired, and started lobbies.
     */
    public GuestJoinResponse joinAsGuest(String rawCode, GuestJoinRequest request, String clientAddress) {
        String nickname = guestAdmission.check(
                clientAddress, LobbyCode.parse(rawCode).map(LobbyCode::value).orElse(""), request);
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            if (lobby.isFull()) {
                throw failure(ApiErrorCode.LOBBY_FULL);
            }
            if (lobby.hasDisplayName(nickname)) {
                throw failure(ApiErrorCode.GUEST_NICKNAME_TAKEN);
            }
            LobbyParticipant guest = new LobbyParticipant(
                    new GameParticipantId(identityGenerator.nextId()),
                    Optional.empty(),
                    nickname,
                    false);
            Instant now = expirationPolicy.instant();
            LobbySnapshot joined = replace(lobby, lobby.join(guest, now, expiresAt(now)), now);
            return new GuestJoinResponse(
                    guestAdmission.issueToken(sessionId.value(), guest.participantId().value()),
                    response(joined, guest.participantId()));
        }
    }

    /** Removes the caller; the host role moves on when the host leaves. */
    public void leave(UUID callerId, String rawCode) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            removeMember(lobby, requireMember(lobby, callerId).participantId());
        }
    }

    /** Sets the caller's ready flag. */
    public LobbyResponse setReady(UUID callerId, String rawCode, boolean ready) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            LobbyParticipant member = requireMember(lobby, callerId);
            if (member.ready() == ready) {
                return response(lobby, member.participantId());
            }
            Instant now = expirationPolicy.instant();
            LobbySnapshot updated = replace(
                    lobby, lobby.withReady(member.participantId(), ready, now, expiresAt(now)), now);
            return response(updated, member.participantId());
        }
    }

    /** Lets the host choose which devices play the round audio (ADR-0022). */
    public LobbyResponse setAudioMode(UUID callerId, String rawCode, AudioMode audioMode) {
        if (audioMode == null) {
            throw failure(ApiErrorCode.LOBBY_AUDIO_MODE_INVALID);
        }
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            LobbyParticipant host = requireHost(lobby, callerId);
            if (lobby.audioMode() == audioMode) {
                return response(lobby, host.participantId());
            }
            Instant now = expirationPolicy.instant();
            LobbySnapshot updated = replace(
                    lobby, lobby.withAudioMode(audioMode, now, expiresAt(now)), now);
            return response(updated, host.participantId());
        }
    }

    /** Lets the host remove another member. */
    public void kick(UUID callerId, String rawCode, UUID rawParticipantId) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            LobbyParticipant host = requireHost(lobby, callerId);
            GameParticipantId target = new GameParticipantId(rawParticipantId);
            if (target.equals(host.participantId()) || lobby.member(target).isEmpty()) {
                throw failure(ApiErrorCode.LOBBY_PARTICIPANT_NOT_FOUND);
            }
            removeMember(lobby, target);
        }
    }

    /**
     * Lets the host start once everyone is ready: snapshots tracks, creates the game session
     * under the lobby's ID, closes the lobby, and tells members to open the game room.
     */
    public SoloSessionResponse start(UUID callerId, String rawCode) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            LobbyParticipant host = requireHost(lobby, callerId);
            if (!lobby.canStart()) {
                throw failure(ApiErrorCode.LOBBY_NOT_READY);
            }
            GameSetupService.GameSetup setup = gameSetupService.prepare(lobby.configuration());
            Instant now = expirationPolicy.instant();
            List<SessionParticipant> participants = lobby.participants().stream().
                    map(member -> new SessionParticipant(
                            member.participantId(), member.accountId(), member.displayName())).
                    toList();
            ActiveSessionSnapshot session = new ActiveSessionSnapshot(
                    sessionId,
                    host.participantId(),
                    participants,
                    setup.configuration(),
                    GameStateSnapshot.created(
                            setup.configuration(),
                            setup.selection(),
                            participants.stream().map(SessionParticipant::participantId).toList()),
                    SecondaryDiscoveryState.empty(),
                    ProcessedCommandLedger.empty(),
                    0,
                    now,
                    now,
                    expirationPolicy.expiresAt(GameStatus.CREATED, now),
                    0);
            if (sessionStore.create(session, now) != SessionCreateOutcome.CREATED) {
                throw new IllegalStateException("lobby session identity is already in use");
            }
            lobbyStore.remove(sessionId, lobby.version());
            publish(sessionId, session.version(), now, PublicSessionEventType.LOBBY_STARTED,
                    new PublicSessionEventPayload.LobbyStarted(
                            setup.selection().tracks().size()));
            return new SoloSessionResponse(
                    sessionId.value(),
                    host.participantId().value(),
                    session.gameState().status().name(),
                    session.version(),
                    session.expiresAt(),
                    SessionConnectionInstructions.forSession(sessionId.value()));
        }
    }

    /** Frees join codes of lobbies abandoned for the inactivity window. */
    @Scheduled(fixedDelayString = "${app.session.cleanup-delay:PT1M}")
    public void removeExpired() {
        lobbyStore.removeExpired(expirationPolicy.instant());
    }

    private void removeMember(LobbySnapshot lobby, GameParticipantId participantId) {
        Instant now = expirationPolicy.instant();
        Optional<LobbySnapshot> remaining = lobby.leave(participantId, now, expiresAt(now));
        if (remaining.isEmpty()) {
            lobbyStore.remove(lobby.sessionId(), lobby.version());
            return;
        }
        replace(lobby, remaining.get(), now);
    }

    private LobbySnapshot replace(LobbySnapshot lobby, LobbySnapshot next, Instant now) {
        if (!lobbyStore.compareAndSet(lobby.version(), next, now)) {
            throw failure(ApiErrorCode.LOBBY_NOT_FOUND);
        }
        publish(next.sessionId(), next.version(), now, PublicSessionEventType.LOBBY_UPDATED,
                new PublicSessionEventPayload.LobbyUpdated(
                        next.code().value(),
                        next.hostParticipantId().value(),
                        next.participants().stream().
                                map(member -> new PublicSessionEventPayload.LobbyMember(
                                        member.participantId().value(),
                                        member.displayName(),
                                        member.ready(),
                                        next.isHost(member.participantId()),
                                        !member.registered())).
                                toList(),
                        next.canStart(),
                        next.audioMode()));
        return next;
    }

    private void publish(
            SessionId sessionId,
            long version,
            Instant now,
            PublicSessionEventType type,
            PublicSessionEventPayload payload) {
        eventPublisher.publishPublic(new PublicSessionEvent(
                new SessionEventMetadata(
                        SessionProtocol.VERSION, identityGenerator.nextId(), sessionId.value(),
                        version, now),
                type,
                payload));
    }

    private LobbySnapshot requireLobby(String rawCode) {
        // Malformed, unknown, expired, and started codes are deliberately indistinguishable.
        return LobbyCode.parse(rawCode).
                flatMap(code -> lobbyStore.findByCode(code, expirationPolicy.instant())).
                orElseThrow(() -> failure(ApiErrorCode.LOBBY_NOT_FOUND));
    }

    private LobbySnapshot current(SessionId sessionId) {
        return lobbyStore.find(sessionId, expirationPolicy.instant()).
                orElseThrow(() -> failure(ApiErrorCode.LOBBY_NOT_FOUND));
    }

    private LobbyParticipant requireMember(LobbySnapshot lobby, UUID callerId) {
        return lobby.memberFor(callerId).orElseThrow(() -> failure(ApiErrorCode.LOBBY_NOT_FOUND));
    }

    private LobbyParticipant requireHost(LobbySnapshot lobby, UUID callerId) {
        LobbyParticipant member = requireMember(lobby, callerId);
        if (!lobby.isHost(member.participantId())) {
            throw failure(ApiErrorCode.LOBBY_HOST_REQUIRED);
        }
        return member;
    }

    private LobbyParticipant newMember(UserEntity user) {
        return new LobbyParticipant(
                new GameParticipantId(identityGenerator.nextId()),
                Optional.of(user.getId()),
                user.getUsername(),
                false);
    }

    private Instant expiresAt(Instant now) {
        return expirationPolicy.expiresAt(GameStatus.CREATED, now);
    }

    private LobbyResponse response(LobbySnapshot lobby, GameParticipantId caller) {
        return new LobbyResponse(
                lobby.sessionId().value(),
                lobby.code().value(),
                caller.value(),
                lobby.hostParticipantId().value(),
                lobby.participants().stream().
                        map(member -> new LobbyParticipantResponse(
                                member.participantId().value(),
                                member.displayName(),
                                member.ready(),
                                lobby.isHost(member.participantId()),
                                !member.registered())).
                        toList(),
                lobby.canStart(),
                GameStateSnapshot.MAXIMUM_PARTICIPANTS,
                lobby.version(),
                lobby.expiresAt(),
                SessionConnectionInstructions.forSession(lobby.sessionId().value()),
                gameSetupService.playlistName(lobby.configuration().playlistId()),
                lobby.configuration().requestedTrackCount(),
                lobby.configuration().maximumRoundDurationSeconds(),
                lobby.audioMode());
    }

    private ResponseStatusException failure(ApiErrorCode code) {
        return new ResponseStatusException(code.getStatus(), code.name());
    }

    private Object lockFor(SessionId sessionId) {
        return locks[Math.floorMod(sessionId.hashCode(), LOCK_STRIPES)];
    }
}
