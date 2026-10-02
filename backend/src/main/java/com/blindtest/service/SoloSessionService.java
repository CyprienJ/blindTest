package com.blindtest.service;

import com.blindtest.model.dto.CreateSoloSessionRequest;
import com.blindtest.model.dto.SessionConnectionInstructions;
import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.game.GameConfiguration;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.GameTrackSelection;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.service.port.SessionIdentityGenerator;
import com.blindtest.store.SessionCreateOutcome;
import com.blindtest.store.SessionStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Creates immutable, one-player active sessions from authoritative catalogue data. */
@Service
public class SoloSessionService {

    private static final int MAXIMUM_ID_ATTEMPTS = 5;

    private final GameSetupService gameSetupService;
    private final SessionStore sessionStore;
    private final SessionIdentityGenerator identityGenerator;
    private final SessionExpirationPolicy expirationPolicy;

    /** Wires game setup, identity, time, and active-state boundaries. */
    public SoloSessionService(
            GameSetupService gameSetupService,
            SessionStore sessionStore,
            SessionIdentityGenerator identityGenerator,
            SessionExpirationPolicy expirationPolicy) {
        this.gameSetupService = gameSetupService;
        this.sessionStore = sessionStore;
        this.identityGenerator = identityGenerator;
        this.expirationPolicy = expirationPolicy;
    }

    /** Validates and snapshots a new solo session owned by the authenticated account. */
    public SoloSessionResponse create(UUID accountId, CreateSoloSessionRequest request) {
        UserEntity owner = gameSetupService.requireVerifiedPlayer(accountId);
        GameSetupService.GameSetup setup =
                gameSetupService.prepare(gameSetupService.toRequestedConfiguration(request));
        return createSnapshot(owner, setup.configuration(), setup.selection());
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
                    GameStateSnapshot.created(configuration, selection, List.of(participantId)),
                    SecondaryDiscoveryState.empty(),
                    ProcessedCommandLedger.empty(),
                    0,
                    now,
                    now,
                    expirationPolicy.expiresAt(GameStatus.CREATED, now),
                    0);
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
                SessionConnectionInstructions.forSession(snapshot.sessionId().value()));
    }
}
