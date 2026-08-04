package com.blindtest.service;

import com.blindtest.model.game.AnswerMatchResult;
import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameConfiguration;
import com.blindtest.model.game.GameConfigurationValidator;
import com.blindtest.model.game.GameMediaSnapshot;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GamePlaybackSnapshot;
import com.blindtest.model.game.GameStateMachine;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameTrackSelection;
import com.blindtest.model.game.GameTrackSnapshot;
import com.blindtest.model.game.PlaylistEligibilitySnapshot;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.game.RoundScoringWindow;
import com.blindtest.model.game.RoundStateMachine;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.game.TitleScoreCalculator;
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

final class FinishedSessionFixture {

    static final Instant START = Instant.parse("2026-01-31T10:00:00Z");
    static final UUID SESSION_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    static final UUID ACCOUNT_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    static final UUID PARTICIPANT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000003");
    static final UUID PLAYLIST_ID = UUID.fromString("10000000-0000-4000-8000-000000000004");

    private FinishedSessionFixture() {
    }

    static ActiveSessionSnapshot finished() {
        GameConfiguration configuration = new GameConfigurationValidator().validate(
                new RequestedGameConfiguration(PLAYLIST_ID, 5, 30, Map.of()),
                new PlaylistEligibilitySnapshot(PLAYLIST_ID, 5));
        GameStateSnapshot game = GameStateSnapshot.created(
                configuration, new GameTrackSelection(tracks()));
        GameStateMachine machine = new GameStateMachine(
                new RoundStateMachine(new TitleScoreCalculator()));
        game = machine.start(game);
        Instant completedAt = START;
        for (int index = 0; index < 5; index++) {
            Instant startsAt = START.plusSeconds(index * 40L);
            RoundScoringWindow window = new RoundScoringWindow(
                    startsAt.plusSeconds(30), Duration.ofSeconds(30));
            game = machine.startRound(game, window);
            if (index == 0) {
                game = machine.resolveTitle(
                        game, AnswerMatchResult.matched(GameAnswerType.TITLE), startsAt);
                completedAt = startsAt.plusSeconds(4);
            } else if (index == 1) {
                game = machine.timeoutRound(game, window.deadline());
                completedAt = window.deadline().plusSeconds(4);
            } else {
                game = machine.skipRound(game, startsAt.plusSeconds(2));
                completedAt = startsAt.plusSeconds(6);
            }
            game = machine.completeRound(game, completedAt).game();
        }
        GameParticipantId participantId = new GameParticipantId(PARTICIPANT_ID);
        return new ActiveSessionSnapshot(
                new SessionId(SESSION_ID),
                participantId,
                List.of(new SessionParticipant(
                        participantId, Optional.of(ACCOUNT_ID), "Historical owner")),
                configuration,
                game,
                SecondaryDiscoveryState.empty(),
                ProcessedCommandLedger.empty(),
                17,
                START.minusSeconds(10),
                completedAt,
                completedAt.plus(Duration.ofHours(1)));
    }

    private static List<GameTrackSnapshot> tracks() {
        List<GameTrackSnapshot> tracks = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            Map<GameAnswerType, List<String>> aliases = index == 0
                    ? Map.of(GameAnswerType.TITLE, List.of("First alias")) : Map.of();
            tracks.add(new GameTrackSnapshot(
                    UUID.nameUUIDFromBytes(("history-track-" + index).getBytes()),
                    index,
                    new GameAnswerSnapshot(
                            "Title " + index,
                            "Artist " + index,
                            "Composer " + index,
                            "Album " + index,
                            aliases),
                    new GamePlaybackSnapshot(index, 180),
                    new GameMediaSnapshot(
                            "private/history-track-" + index + ".mp3", "checksum-" + index)));
        }
        return tracks;
    }
}
