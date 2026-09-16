package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameStateMachineTest {

    private static final UUID PLAYLIST_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final Instant START = Instant.parse("2026-08-04T12:00:00Z");

    private final GameStateMachine machine = new GameStateMachine(
            new RoundStateMachine(new TitleScoreCalculator()));

    @Test
    void progressesOrderedRoundsAndBuildsFinalSummary() {
        GameStateSnapshot game = machine.start(createdGame(3));

        game = machine.startRound(game, window(START));
        game = machine.resolveTitle(
                game, AnswerMatchResult.matched(GameAnswerType.TITLE), START);
        RoundCompletionTransition first = machine.completeRound(game, START.plusSeconds(1));

        assertThat(first.progression()).isEqualTo(GameProgression.NEXT_ROUND_PREPARED);
        assertThat(first.game().currentRoundIndex()).isEqualTo(1);
        assertThat(first.game().currentRound().orElseThrow().track().playOrder()).isEqualTo(1);
        assertThat(first.game().completedRounds().getFirst().playbackWindow()).
                contains(RoundPlaybackWindow.from(
                        first.game().trackSelection().tracks().getFirst().playback(),
                        window(START)));
        assertThat(first.game().currentRound().orElseThrow().playbackWindow()).isEmpty();
        assertThat(first.game().completedRounds()).hasSize(1);

        game = machine.startRound(first.game(), window(START.plusSeconds(70)));
        assertThat(game.currentRound().orElseThrow().playbackWindow()).
                contains(RoundPlaybackWindow.from(
                        game.trackSelection().tracks().get(1).playback(),
                        window(START.plusSeconds(70))));
        game = machine.timeoutRound(game, START.plusSeconds(130));
        RoundCompletionTransition second =
                machine.completeRound(game, START.plusSeconds(131));

        game = machine.startRound(second.game(), window(START.plusSeconds(140)));
        game = machine.skipRound(game, START.plusSeconds(150));
        RoundCompletionTransition last =
                machine.completeRound(game, START.plusSeconds(151));

        assertThat(last.progression()).isEqualTo(GameProgression.GAME_FINISHED);
        assertThat(last.game().status()).isEqualTo(GameStatus.FINISHED);
        assertThat(last.game().currentRound()).isEmpty();
        assertThat(last.game().currentRoundIndex()).isEqualTo(3);
        assertThat(last.game().completedRounds()).hasSize(3);

        GameSummary summary = last.game().finalSummary().orElseThrow();
        assertThat(summary.totalScore()).isEqualTo(1000);
        assertThat(summary.totalRounds()).isEqualTo(3);
        assertThat(summary.titlesFound()).isOne();
        assertThat(summary.timedOutRounds()).isOne();
        assertThat(summary.skippedRounds()).isOne();
        assertThat(summary.rounds()).extracting(RoundResultSummary::roundIndex).
                containsExactly(0, 1, 2);
        assertThat(summary.rounds()).extracting(RoundResultSummary::outcome).
                containsExactly(
                        RoundOutcome.TITLE_FOUND, RoundOutcome.TIMEOUT, RoundOutcome.SKIPPED);
    }

    @Test
    void lastRoundProducesOneCompletionAndTerminalStateRejectsReplay() {
        GameStateSnapshot game = machine.start(createdGame(1));
        game = machine.startRound(game, window(START));
        game = machine.skipRound(game, START.plusSeconds(1));

        RoundCompletionTransition completion =
                machine.completeRound(game, START.plusSeconds(2));

        assertThat(completion.progression()).isEqualTo(GameProgression.GAME_FINISHED);
        assertFailure(
                () -> machine.completeRound(completion.game(), START.plusSeconds(3)),
                GameTransitionError.ILLEGAL_GAME_TRANSITION);
        assertFailure(
                () -> machine.start(completion.game()),
                GameTransitionError.ILLEGAL_GAME_TRANSITION);
        assertFailure(
                () -> machine.abort(completion.game()),
                GameTransitionError.ILLEGAL_GAME_TRANSITION);
    }

    @Test
    void createdAndPlayingGamesCanAbortWithoutFinalSummary() {
        GameStateSnapshot created = createdGame(2);
        GameStateSnapshot playing = machine.start(created);

        GameStateSnapshot abortedCreated = machine.abort(created);
        GameStateSnapshot abortedPlaying = machine.abort(playing);

        assertThat(abortedCreated.status()).isEqualTo(GameStatus.ABORTED);
        assertThat(abortedCreated.currentRound()).isEmpty();
        assertThat(abortedPlaying.status()).isEqualTo(GameStatus.ABORTED);
        assertThat(abortedPlaying.currentRound()).isPresent();
        assertThat(abortedCreated.finalSummary()).isEmpty();
        assertThat(abortedPlaying.finalSummary()).isEmpty();
    }

    @Test
    void activeGameSnapshotsRemainImmutableAcrossProgression() {
        GameStateSnapshot started = machine.start(createdGame(2));
        GameStateSnapshot playing = machine.startRound(started, window(START));
        GameStateSnapshot revealed = machine.skipRound(playing, START.plusSeconds(1));
        RoundCompletionTransition progressed =
                machine.completeRound(revealed, START.plusSeconds(2));

        assertThat(started.currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.PREPARING);
        assertThat(playing.currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.PLAYING);
        assertThat(revealed.currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.REVEALED);
        assertThat(progressed.game().currentRound().orElseThrow().status()).
                isEqualTo(RoundStatus.PREPARING);
        assertThatThrownBy(() -> progressed.game().completedRounds().clear()).
                isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void illegalAggregateTransitionsReturnStableFailures() {
        GameStateSnapshot created = createdGame(1);
        GameStateSnapshot playing = machine.start(created);

        assertFailure(
                () -> machine.startRound(created, window(START)),
                GameTransitionError.ILLEGAL_GAME_TRANSITION);
        assertFailure(
                () -> machine.start(playing),
                GameTransitionError.ILLEGAL_GAME_TRANSITION);
        assertFailure(
                () -> machine.startRound(
                        playing,
                        new RoundScoringWindow(
                                START.plusSeconds(30), Duration.ofSeconds(30))),
                GameTransitionError.ROUND_TIME_INVALID);
        assertFailure(
                () -> machine.completeRound(playing, START),
                GameTransitionError.ILLEGAL_ROUND_TRANSITION);
    }

    @Test
    void gameSnapshotRejectsMismatchedSelectionAndInvalidSummary() {
        GameConfiguration configuration = new GameConfiguration(PLAYLIST_ID, 2, 60);
        GameTrackSelection oneTrack = selection(1);

        assertThatThrownBy(() -> GameStateSnapshot.created(configuration, oneTrack)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("track selection must match configured count");

        GameStateSnapshot finished = finishSingleSkippedGame();
        RoundResultSummary differentRound = new RoundResultSummary(
                0,
                finished.completedRounds().getFirst().track().trackId(),
                RoundOutcome.TIMEOUT,
                0,
                TitleScoreCalculator.SCORING_RULE_VERSION);
        GameSummary invalidSummary = new GameSummary(
                0, 1, 0, 0, 1, List.of(differentRound));
        assertThatThrownBy(() -> new GameStateSnapshot(
                        finished.configuration(),
                        finished.trackSelection(),
                        GameStatus.FINISHED,
                        1,
                        Optional.empty(),
                        finished.completedRounds(),
                        Optional.of(invalidSummary))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("game snapshot does not match its status");
    }

    private GameStateSnapshot finishSingleSkippedGame() {
        GameStateSnapshot game = machine.start(createdGame(1));
        game = machine.startRound(game, window(START));
        game = machine.skipRound(game, START.plusSeconds(1));
        return machine.completeRound(game, START.plusSeconds(2)).game();
    }

    private GameStateSnapshot createdGame(int trackCount) {
        GameConfiguration configuration =
                new GameConfiguration(PLAYLIST_ID, trackCount, 60);
        return GameStateSnapshot.created(configuration, selection(trackCount));
    }

    private GameTrackSelection selection(int trackCount) {
        List<GameTrackSnapshot> tracks = new ArrayList<>();
        for (int index = 0; index < trackCount; index++) {
            tracks.add(RoundStateMachineTest.track(index));
        }
        return new GameTrackSelection(tracks);
    }

    private RoundScoringWindow window(Instant startsAt) {
        return new RoundScoringWindow(startsAt.plusSeconds(60), Duration.ofSeconds(60));
    }

    private void assertFailure(Runnable action, GameTransitionError expected) {
        assertThatThrownBy(action::run).
                isInstanceOfSatisfying(GameTransitionException.class, failure -> {
                    assertThat(failure.error()).isEqualTo(expected);
                    assertThat(failure.code()).isEqualTo(expected.code());
                });
    }
}
