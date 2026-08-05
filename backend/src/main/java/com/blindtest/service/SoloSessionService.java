package com.blindtest.service;

import com.blindtest.error.ApiErrorCode;
import com.blindtest.model.dto.CreateSoloSessionRequest;
import com.blindtest.model.dto.SessionConnectionInstructions;
import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackAnswerAliasEntity;
import com.blindtest.model.entity.TrackAnswerType;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameConfiguration;
import com.blindtest.model.game.GameConfigurationError;
import com.blindtest.model.game.GameConfigurationValidationException;
import com.blindtest.model.game.GameConfigurationValidator;
import com.blindtest.model.game.GameMediaSnapshot;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GamePlaybackSnapshot;
import com.blindtest.model.game.GameRandom;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.GameTrackCandidate;
import com.blindtest.model.game.GameTrackSelection;
import com.blindtest.model.game.GameTrackSelector;
import com.blindtest.model.game.PlaylistEligibilitySnapshot;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackAnswerAliasRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.port.SessionIdentityGenerator;
import com.blindtest.store.SessionCreateOutcome;
import com.blindtest.store.SessionStore;
import com.blindtest.websocket.protocol.SessionProtocol;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Creates immutable, one-player active sessions from authoritative catalogue data. */
@Service
public class SoloSessionService {

    private static final int MAXIMUM_ID_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackAnswerAliasRepository aliasRepository;
    private final SessionStore sessionStore;
    private final SessionIdentityGenerator identityGenerator;
    private final SessionExpirationPolicy expirationPolicy;
    private final GameTrackSelector trackSelector;
    private final GameConfigurationValidator configurationValidator =
            new GameConfigurationValidator();

    /** Wires catalogue, identity, random-selection, time, and active-state boundaries. */
    public SoloSessionService(
            UserRepository userRepository,
            PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository,
            TrackAnswerAliasRepository aliasRepository,
            SessionStore sessionStore,
            SessionIdentityGenerator identityGenerator,
            SessionExpirationPolicy expirationPolicy,
            GameRandom gameRandom) {
        this.userRepository = userRepository;
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.aliasRepository = aliasRepository;
        this.sessionStore = sessionStore;
        this.identityGenerator = identityGenerator;
        this.expirationPolicy = expirationPolicy;
        this.trackSelector = new GameTrackSelector(gameRandom);
    }

    /** Validates and snapshots a new solo session owned by the authenticated account. */
    @Transactional(readOnly = true)
    public SoloSessionResponse create(UUID accountId, CreateSoloSessionRequest request) {
        UserEntity owner = requireVerifiedOwner(accountId);
        RequestedGameConfiguration requested = toRequestedConfiguration(request);
        if (requested == null) {
            throw new GameConfigurationValidationException(
                    GameConfigurationError.CONFIGURATION_REQUIRED);
        }
        UUID playlistId = requested.playlistId();
        List<GameTrackCandidate> candidates = loadCandidates(playlistId);
        PlaylistEligibilitySnapshot eligibility = playlistId != null
                && playlistRepository.existsById(playlistId)
                ? new PlaylistEligibilitySnapshot(playlistId, candidates.size())
                : null;
        GameConfiguration configuration =
                configurationValidator.validate(requested, eligibility);
        GameTrackSelection selection = trackSelector.select(configuration, candidates);
        return createSnapshot(owner, configuration, selection);
    }

    private UserEntity requireVerifiedOwner(UUID accountId) {
        UserEntity owner = userRepository.findById(accountId).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_ACCESS_TOKEN.name()));
        if (!owner.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, ApiErrorCode.EMAIL_NOT_VERIFIED.name());
        }
        return owner;
    }

    private RequestedGameConfiguration toRequestedConfiguration(CreateSoloSessionRequest request) {
        if (request == null) {
            return null;
        }
        return new RequestedGameConfiguration(
                request.playlistId(),
                request.trackCount(),
                request.maxRoundSeconds(),
                request.extensions());
    }

    private List<GameTrackCandidate> loadCandidates(UUID playlistId) {
        if (playlistId == null) {
            return List.of();
        }
        List<PlaylistTrackEntity> memberships =
                playlistTrackRepository.findPublishedByPlaylistIdOrdered(
                        playlistId, TrackStatus.PUBLISHED);
        List<TrackEntity> playableTracks = memberships.stream().
                map(PlaylistTrackEntity::getTrack).
                filter(this::hasSnapshotMedia).
                toList();
        if (playableTracks.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<TrackAnswerAliasEntity>> aliases = aliasRepository.
                findAllByTrackIdIn(playableTracks.stream().map(TrackEntity::getId).toList()).
                stream().collect(Collectors.groupingBy(alias -> alias.getTrack().getId()));
        return playableTracks.stream().
                map(track -> toCandidate(track, aliases.getOrDefault(track.getId(), List.of()))).
                toList();
    }

    private boolean hasSnapshotMedia(TrackEntity track) {
        return hasText(track.getAudioObjectKey()) && hasText(track.getAudioChecksum());
    }

    private GameTrackCandidate toCandidate(
            TrackEntity track,
            List<TrackAnswerAliasEntity> aliases) {
        Map<GameAnswerType, List<String>> groupedAliases = new EnumMap<>(GameAnswerType.class);
        for (TrackAnswerAliasEntity alias : aliases) {
            groupedAliases.computeIfAbsent(toGameAnswerType(alias.getAnswerType()), ignored ->
                    new ArrayList<>()).add(alias.getValue());
        }
        return new GameTrackCandidate(
                track.getId(),
                true,
                new GameAnswerSnapshot(
                        track.getTitle(),
                        track.getArtist(),
                        track.getComposer(),
                        track.getAlbum(),
                        groupedAliases),
                new GamePlaybackSnapshot(
                        track.getExcerptStartSeconds(), track.getDurationSeconds()),
                new GameMediaSnapshot(
                        track.getAudioObjectKey(), track.getAudioChecksum()));
    }

    private GameAnswerType toGameAnswerType(TrackAnswerType answerType) {
        return GameAnswerType.valueOf(answerType.name());
    }

    private SoloSessionResponse createSnapshot(
            UserEntity owner,
            GameConfiguration configuration,
            GameTrackSelection selection) {
        Instant now = expirationPolicy.instant();
        GameParticipantId participantId =
                new GameParticipantId(identityGenerator.nextId());
        SessionParticipant participant = new SessionParticipant(
                participantId, java.util.Optional.of(owner.getId()), owner.getUsername());
        for (int attempt = 0; attempt < MAXIMUM_ID_ATTEMPTS; attempt++) {
            SessionId sessionId = new SessionId(identityGenerator.nextId());
            ActiveSessionSnapshot snapshot = new ActiveSessionSnapshot(
                    sessionId,
                    participantId,
                    List.of(participant),
                    configuration,
                    GameStateSnapshot.created(configuration, selection),
                    SecondaryDiscoveryState.empty(),
                    ProcessedCommandLedger.empty(),
                    0,
                    now,
                    now,
                    expirationPolicy.expiresAt(GameStatus.CREATED, now));
            if (sessionStore.create(snapshot, now) == SessionCreateOutcome.CREATED) {
                return response(snapshot, participantId);
            }
        }
        throw new IllegalStateException("could not allocate a unique session identity");
    }

    private SoloSessionResponse response(
            ActiveSessionSnapshot snapshot,
            GameParticipantId participantId) {
        return new SoloSessionResponse(
                snapshot.sessionId().value(),
                participantId.value(),
                snapshot.gameState().status().name(),
                snapshot.version(),
                snapshot.expiresAt(),
                new SessionConnectionInstructions(
                        SessionProtocol.VERSION,
                        SessionProtocol.WEB_SOCKET_ENDPOINT,
                        SessionProtocol.SOCK_JS_ENDPOINT,
                        SessionProtocol.commandDestination(snapshot.sessionId().value()),
                        SessionProtocol.publicEventDestination(snapshot.sessionId().value()),
                        SessionProtocol.privateEventDestination(snapshot.sessionId().value())));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
