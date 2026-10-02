package com.blindtest.service;

import com.blindtest.error.ApiErrorCode;
import com.blindtest.model.dto.CreateSoloSessionRequest;
import com.blindtest.model.dto.LobbyParticipantResponse;
import com.blindtest.model.dto.LobbyResponse;
import com.blindtest.model.dto.SessionConnectionInstructions;
import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.lobby.LobbyCode;
import com.blindtest.model.lobby.LobbyParticipant;
import com.blindtest.model.lobby.LobbySnapshot;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.repository.PlaylistRepository;
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
 * Multiplayer lobby lifecycle from creation to start (ADR-0019, MP-02). Membership changes go
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
    private final PlaylistRepository playlistRepository;
    private final Object[] locks = new Object[LOCK_STRIPES];

    /** Wires game setup, lobby and session state, identities, time, events, and playlists. */
    public LobbyService(
            GameSetupService gameSetupService,
            LobbyStore lobbyStore,
            SessionStore sessionStore,
            SessionIdentityGenerator identityGenerator,
            LobbyCodeGenerator codeGenerator,
            SessionExpirationPolicy expirationPolicy,
            SessionEventPublisher eventPublisher,
            PlaylistRepository playlistRepository) {
        this.gameSetupService = gameSetupService;
        this.lobbyStore = lobbyStore;
        this.sessionStore = sessionStore;
        this.identityGenerator = identityGenerator;
        this.codeGenerator = codeGenerator;
        this.expirationPolicy = expirationPolicy;
        this.eventPublisher = eventPublisher;
        this.playlistRepository = playlistRepository;
        Arrays.setAll(locks, ignored -> new Object());
    }

    /** Validates the configuration against the catalogue now and opens a lobby hosted by the caller. */
    public LobbyResponse create(UUID accountId, CreateSoloSessionRequest request) {
        UserEntity host = gameSetupService.requireVerifiedPlayer(accountId);
        RequestedGameConfiguration requested = gameSetupService.toRequestedConfiguration(request);
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
                    now,
                    expiresAt(now));
            if (lobbyStore.create(lobby, now)) {
                return response(lobby, member.participantId());
            }
        }
        throw new IllegalStateException("could not allocate a unique lobby code");
    }

    /** Returns the lobby to one of its members; anyone else gets the not-found response. */
    public LobbyResponse get(UUID accountId, String rawCode) {
        LobbySnapshot lobby = requireLobby(rawCode);
        LobbyParticipant member = requireMember(lobby, accountId);
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

    /** Removes the caller; the host role moves on when the host leaves. */
    public void leave(UUID accountId, String rawCode) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            removeMember(lobby, requireMember(lobby, accountId).participantId());
        }
    }

    /** Sets the caller's ready flag. */
    public LobbyResponse setReady(UUID accountId, String rawCode, boolean ready) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            LobbyParticipant member = requireMember(lobby, accountId);
            if (member.ready() == ready) {
                return response(lobby, member.participantId());
            }
            Instant now = expirationPolicy.instant();
            LobbySnapshot updated = replace(
                    lobby, lobby.withReady(member.participantId(), ready, now, expiresAt(now)), now);
            return response(updated, member.participantId());
        }
    }

    /** Lets the host remove another member. */
    public void kick(UUID accountId, String rawCode, UUID rawParticipantId) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            LobbyParticipant host = requireHost(lobby, accountId);
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
    public SoloSessionResponse start(UUID accountId, String rawCode) {
        SessionId sessionId = requireLobby(rawCode).sessionId();
        synchronized (lockFor(sessionId)) {
            LobbySnapshot lobby = current(sessionId);
            LobbyParticipant host = requireHost(lobby, accountId);
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
                        next.canStart()));
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

    private LobbyParticipant requireMember(LobbySnapshot lobby, UUID accountId) {
        return lobby.memberFor(accountId).orElseThrow(() -> failure(ApiErrorCode.LOBBY_NOT_FOUND));
    }

    private LobbyParticipant requireHost(LobbySnapshot lobby, UUID accountId) {
        LobbyParticipant member = requireMember(lobby, accountId);
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
                playlistRepository.findById(lobby.configuration().playlistId()).
                        map(PlaylistEntity::getName).
                        orElse("Unavailable playlist"),
                lobby.configuration().requestedTrackCount(),
                lobby.configuration().maximumRoundDurationSeconds());
    }

    private ResponseStatusException failure(ApiErrorCode code) {
        return new ResponseStatusException(code.getStatus(), code.name());
    }

    private Object lockFor(SessionId sessionId) {
        return locks[Math.floorMod(sessionId.hashCode(), LOCK_STRIPES)];
    }
}
