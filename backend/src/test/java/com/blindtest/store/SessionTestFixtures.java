package com.blindtest.store;

import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameConfiguration;
import com.blindtest.model.game.GameConfigurationValidator;
import com.blindtest.model.game.GameMediaSnapshot;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GamePlaybackSnapshot;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameTrackSelection;
import com.blindtest.model.game.GameTrackSnapshot;
import com.blindtest.model.game.PlaylistEligibilitySnapshot;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.ProcessedCommandLedger;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class SessionTestFixtures {

    static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final UUID PLAYLIST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final GameParticipantId OWNER_ID = new GameParticipantId(
            UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private SessionTestFixtures() {
    }

    static ActiveSessionSnapshot session() {
        return session(List.of(owner()));
    }

    static ActiveSessionSnapshot session(List<SessionParticipant> participants) {
        return session(1, participants);
    }

    static ActiveSessionSnapshot sessionWithSuffix(int suffix) {
        return session(suffix, List.of(owner()));
    }

    static SessionParticipant owner() {
        return new SessionParticipant(OWNER_ID, Optional.of(ACCOUNT_ID), "Owner");
    }

    static ActiveSessionSnapshot copy(
            ActiveSessionSnapshot source,
            GameParticipantId ownerParticipantId,
            List<SessionParticipant> participants,
            long version,
            Instant updatedAt,
            Instant expiresAt) {
        return new ActiveSessionSnapshot(
                source.sessionId(),
                ownerParticipantId,
                participants,
                source.configuration(),
                source.gameState(),
                source.discoveries(),
                source.processedCommands(),
                version,
                source.createdAt(),
                updatedAt,
                expiresAt, 0);
    }

    static ActiveSessionSnapshot recreatedAt(
            ActiveSessionSnapshot source,
            Instant createdAt) {
        return new ActiveSessionSnapshot(
                source.sessionId(),
                source.ownerParticipantId(),
                source.participants(),
                source.configuration(),
                source.gameState(),
                SecondaryDiscoveryState.empty(),
                ProcessedCommandLedger.empty(),
                0,
                createdAt,
                createdAt,
                createdAt.plus(Duration.ofMinutes(30)), 0);
    }

    private static ActiveSessionSnapshot session(
            int sessionSuffix,
            List<SessionParticipant> participants) {
        GameConfiguration configuration = new GameConfigurationValidator().validate(
                new RequestedGameConfiguration(PLAYLIST_ID, 5, 30, Map.of()),
                new PlaylistEligibilitySnapshot(PLAYLIST_ID, 5));
        GameStateSnapshot gameState = GameStateSnapshot.created(
                configuration,
                new GameTrackSelection(tracks()),
                participants.stream().map(SessionParticipant::participantId).toList());
        return new ActiveSessionSnapshot(
                new SessionId(UUID.fromString(
                        "10000000-0000-0000-0000-" + String.format("%012d", sessionSuffix))),
                OWNER_ID,
                participants,
                configuration,
                gameState,
                SecondaryDiscoveryState.empty(),
                ProcessedCommandLedger.empty(),
                0,
                NOW,
                NOW,
                NOW.plus(Duration.ofMinutes(30)), 0);
    }

    private static List<GameTrackSnapshot> tracks() {
        List<GameTrackSnapshot> tracks = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            tracks.add(new GameTrackSnapshot(
                    UUID.fromString(
                            "20000000-0000-0000-0000-" + String.format("%012d", index + 1)),
                    index,
                    new GameAnswerSnapshot(
                            "Title " + index, "Artist " + index, null, null, Map.of()),
                    new GamePlaybackSnapshot(0, 180),
                    new GameMediaSnapshot(
                            "tracks/" + index + "/audio.mp3", "checksum-" + index)));
        }
        return tracks;
    }
}
