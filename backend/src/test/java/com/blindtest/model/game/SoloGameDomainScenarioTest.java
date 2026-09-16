package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoloGameDomainScenarioTest {

    private static final UUID PLAYLIST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final GameParticipantId PLAYER = new GameParticipantId(
            UUID.fromString("00000000-0000-0000-0000-000000000200"));

    @Test
    void deterministicSoloGameRunsEntirelyThroughPureDomainCommands() {
        MutableTestClock clock = new MutableTestClock(
                Instant.parse("2026-08-04T12:00:00Z"));
        GameConfiguration configuration = new GameConfigurationValidator().validate(
                new RequestedGameConfiguration(PLAYLIST_ID, 5, 30, Map.of()),
                new PlaylistEligibilitySnapshot(PLAYLIST_ID, 7));
        Random random = new Random(8723);
        GameTrackSelection selection = new GameTrackSelector(random::nextInt).
                select(configuration, candidates(7));
        GameStateMachine gameMachine = new GameStateMachine(
                new RoundStateMachine(new TitleScoreCalculator()));
        AnswerMatcher matcher = new AnswerMatcher();
        SecondaryDiscoveryTracker discoveryTracker = new SecondaryDiscoveryTracker();
        GameStateSnapshot game = gameMachine.start(
                GameStateSnapshot.created(configuration, selection));

        for (int roundIndex = 0; roundIndex < 5; roundIndex++) {
            Instant startsAt = clock.instant();
            RoundScoringWindow scoringWindow = new RoundScoringWindow(
                    startsAt.plusSeconds(30), Duration.ofSeconds(30));
            game = gameMachine.startRound(game, scoringWindow);
            GameTrackSnapshot track = game.currentRound().orElseThrow().track();

            if (roundIndex == 0) {
                SecondaryDiscoveryResult artist = discoveryTracker.route(
                        SecondaryDiscoveryState.empty(),
                        PLAYER,
                        matcher.match(track.answers().artist(), track.answers()));
                assertThat(artist.outcome()).isEqualTo(SecondaryDiscoveryOutcome.DISCOVERED);
                assertThat(artist.state().projectFor(PLAYER).discoveredTypes()).
                        containsExactly(GameAnswerType.ARTIST);
                game = gameMachine.resolveTitle(
                        game,
                        matcher.match(track.answers().title(), track.answers()),
                        startsAt.plusSeconds(15));
            } else if (roundIndex == 1) {
                game = gameMachine.skipRound(game, startsAt.plusSeconds(5));
            } else if (roundIndex == 2) {
                game = gameMachine.timeoutRound(game, scoringWindow.deadline());
            } else if (roundIndex == 3) {
                game = gameMachine.resolveTitle(
                        game,
                        matcher.match(track.answers().title(), track.answers()),
                        startsAt.plusSeconds(29));
            } else {
                game = gameMachine.resolveTitle(
                        game,
                        matcher.match(track.answers().title(), track.answers()),
                        startsAt);
            }

            RoundCompletionTransition completion = gameMachine.completeRound(
                    game, game.currentRound().orElseThrow().revealedAt().orElseThrow());
            game = completion.game();
            clock.advance(Duration.ofSeconds(40));
        }

        assertThat(game.status()).isEqualTo(GameStatus.FINISHED);
        assertThat(game.completedRounds()).hasSize(5);
        assertThat(game.finalSummary()).hasValueSatisfying(summary -> {
            assertThat(summary.totalScore()).isEqualTo(1600);
            assertThat(summary.titlesFound()).isEqualTo(3);
            assertThat(summary.skippedRounds()).isOne();
            assertThat(summary.timedOutRounds()).isOne();
        });
    }

    private List<GameTrackCandidate> candidates(int count) {
        List<GameTrackCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            candidates.add(new GameTrackCandidate(
                    UUID.fromString(
                            "00000000-0000-0000-0000-" + String.format("%012d", index + 1)),
                    true,
                    new GameAnswerSnapshot(
                            "Unique title " + index,
                            "Unique artist " + index,
                            "Composer " + index,
                            "Album " + index,
                            Map.of()),
                    new GamePlaybackSnapshot(index, 180),
                    new GameMediaSnapshot(
                            "tracks/" + index + "/audio.mp3", "checksum-" + index)));
        }
        return candidates;
    }

    private static final class MutableTestClock extends Clock {

        private Instant current;

        private MutableTestClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
