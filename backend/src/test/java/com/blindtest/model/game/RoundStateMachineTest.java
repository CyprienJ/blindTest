package com.blindtest.model.game;

import static com.blindtest.model.game.GameTestParticipants.SOLO;
import static com.blindtest.model.game.GameTestParticipants.SOLO_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundStateMachineTest {

    private static final Instant START = Instant.parse("2026-08-04T12:00:00Z");
    private static final RoundScoringWindow WINDOW =
            new RoundScoringWindow(START.plusSeconds(60), Duration.ofSeconds(60));
    private static final AnswerMatchResult TITLE_MATCH =
            AnswerMatchResult.matched(GameAnswerType.TITLE);

    private final RoundStateMachine machine =
            new RoundStateMachine(new TitleScoreCalculator());

    @Test
    void followsPreparingPlayingRevealedCompletedForCorrectTitle() {
        RoundStateSnapshot preparing = RoundStateSnapshot.preparing(track(0));

        RoundStateSnapshot playing = machine.start(preparing, WINDOW);
        RoundStateSnapshot revealed =
                machine.awardTitle(playing, SOLO, TITLE_MATCH, START.plusSeconds(15), SOLO_ACTIVE);
        RoundStateSnapshot completed = machine.complete(revealed, START.plusSeconds(20));

        assertThat(preparing.status()).isEqualTo(RoundStatus.PREPARING);
        assertThat(playing.status()).isEqualTo(RoundStatus.PLAYING);
        assertThat(playing.playbackWindow()).contains(
                RoundPlaybackWindow.from(track(0).playback(), WINDOW));
        assertThat(revealed.status()).isEqualTo(RoundStatus.REVEALED);
        assertThat(revealed.outcome()).contains(RoundOutcome.TITLE_FOUND);
        assertThat(revealed.scoreState().totalPoints()).isEqualTo(750);
        assertThat(completed.status()).isEqualTo(RoundStatus.COMPLETED);
        assertThat(completed.completedAt()).contains(START.plusSeconds(20));
    }

    @Test
    void correctTitleAtOrAfterDeadlineResolvesAsTimeoutWithoutPoints() {
        RoundStateSnapshot playing = machine.start(RoundStateSnapshot.preparing(track(0)), WINDOW);

        RoundStateSnapshot atDeadline =
                machine.awardTitle(playing, SOLO, TITLE_MATCH, WINDOW.deadline(), SOLO_ACTIVE);
        RoundStateSnapshot afterDeadline =
                machine.awardTitle(playing, SOLO, TITLE_MATCH, WINDOW.deadline().plusNanos(1), SOLO_ACTIVE);

        assertThat(atDeadline.outcome()).contains(RoundOutcome.TIMEOUT);
        assertThat(atDeadline.scoreState().totalPoints()).isZero();
        assertThat(afterDeadline.outcome()).contains(RoundOutcome.TIMEOUT);
        assertThat(afterDeadline.scoreState().totalPoints()).isZero();
    }

    @Test
    void skipAndAuthoritativeTimeoutProduceExplicitZeroPointOutcomes() {
        RoundStateSnapshot playing = machine.start(RoundStateSnapshot.preparing(track(0)), WINDOW);

        RoundStateSnapshot skipped = machine.voteSkip(playing, SOLO, START.plusSeconds(10), SOLO_ACTIVE);
        RoundStateSnapshot lateSkip = machine.voteSkip(playing, SOLO, WINDOW.deadline(), SOLO_ACTIVE);
        RoundStateSnapshot timedOut = machine.timeout(playing, WINDOW.deadline());

        assertThat(skipped.outcome()).contains(RoundOutcome.SKIPPED);
        assertThat(lateSkip.outcome()).contains(RoundOutcome.TIMEOUT);
        assertThat(timedOut.outcome()).contains(RoundOutcome.TIMEOUT);
        assertThat(skipped.scoreState().totalPoints()).isZero();
        assertThat(lateSkip.scoreState().totalPoints()).isZero();
        assertThat(timedOut.scoreState().totalPoints()).isZero();
    }

    @Test
    void secondaryOrRejectedAnswerCannotTriggerTitleReveal() {
        RoundStateSnapshot playing = machine.start(RoundStateSnapshot.preparing(track(0)), WINDOW);

        assertFailure(
                () -> machine.awardTitle(
                        playing,
                        SOLO,
                        AnswerMatchResult.matched(GameAnswerType.ARTIST),
                        START.plusSeconds(1),
                        SOLO_ACTIVE),
                GameTransitionError.TITLE_MATCH_REQUIRED);
        assertFailure(
                () -> machine.awardTitle(
                        playing, SOLO, AnswerMatchResult.noMatch(), START.plusSeconds(1), SOLO_ACTIVE),
                GameTransitionError.TITLE_MATCH_REQUIRED);
    }

    @Test
    void timeoutBeforeDeadlineAndEventsBeforeStartFailSafely() {
        RoundStateSnapshot playing = machine.start(RoundStateSnapshot.preparing(track(0)), WINDOW);

        assertFailure(
                () -> machine.timeout(playing, WINDOW.deadline().minusNanos(1)),
                GameTransitionError.ROUND_DEADLINE_NOT_REACHED);
        assertFailure(
                () -> machine.awardTitle(playing, SOLO, TITLE_MATCH, START.minusNanos(1), SOLO_ACTIVE),
                GameTransitionError.ROUND_TIME_INVALID);
        assertFailure(
                () -> machine.voteSkip(playing, SOLO, START.minusNanos(1), SOLO_ACTIVE),
                GameTransitionError.ROUND_TIME_INVALID);
    }

    @Test
    void invalidLifecycleTransitionsFailWithStableError() {
        RoundStateSnapshot preparing = RoundStateSnapshot.preparing(track(0));
        RoundStateSnapshot playing = machine.start(preparing, WINDOW);
        RoundStateSnapshot revealed = machine.voteSkip(playing, SOLO, START.plusSeconds(1), SOLO_ACTIVE);
        RoundStateSnapshot completed = machine.complete(revealed, START.plusSeconds(2));

        assertFailure(
                () -> machine.start(playing, WINDOW),
                GameTransitionError.ILLEGAL_ROUND_TRANSITION);
        assertFailure(
                () -> machine.voteSkip(preparing, SOLO, START, SOLO_ACTIVE),
                GameTransitionError.ILLEGAL_ROUND_TRANSITION);
        assertFailure(
                () -> machine.complete(playing, START),
                GameTransitionError.ILLEGAL_ROUND_TRANSITION);
        assertFailure(
                () -> machine.complete(completed, START.plusSeconds(3)),
                GameTransitionError.ILLEGAL_ROUND_TRANSITION);
        assertFailure(
                () -> machine.complete(revealed, START),
                GameTransitionError.ROUND_TIME_INVALID);
    }

    @Test
    void pausedPlayingRoundFreezesItsRemainingTimeAndScore() {
        RoundStateSnapshot playing = machine.start(RoundStateSnapshot.preparing(track(0)), WINDOW);

        RoundStateSnapshot paused = machine.pause(playing, START.plusSeconds(15));

        assertThat(paused.pausedAt()).contains(START.plusSeconds(15));
        assertFailure(
                () -> machine.awardTitle(paused, SOLO, TITLE_MATCH, START.plusSeconds(16), SOLO_ACTIVE),
                GameTransitionError.ROUND_PAUSED);
        assertFailure(
                () -> machine.voteSkip(paused, SOLO, START.plusSeconds(16), SOLO_ACTIVE),
                GameTransitionError.ROUND_PAUSED);
        assertFailure(
                () -> machine.timeout(paused, WINDOW.deadline()),
                GameTransitionError.ROUND_PAUSED);
        assertFailure(
                () -> machine.pause(paused, START.plusSeconds(16)),
                GameTransitionError.ROUND_PAUSED);

        RoundStateSnapshot resumed = machine.resume(paused, START.plusSeconds(115));
        RoundScoringWindow shifted =
                new RoundScoringWindow(WINDOW.deadline().plusSeconds(100), WINDOW.configuredDuration());

        assertThat(resumed.paused()).isFalse();
        assertThat(resumed.scoringWindow()).contains(shifted);
        assertThat(resumed.playbackWindow()).contains(
                RoundPlaybackWindow.from(track(0).playback(), shifted));
        // 15 s played before the pause and 15 s after it: 30 of 60 s remain, as without a pause.
        RoundStateSnapshot revealed = machine.awardTitle(
                resumed, SOLO, TITLE_MATCH, START.plusSeconds(130), SOLO_ACTIVE);
        assertThat(revealed.scoreState().totalPoints()).isEqualTo(500);
    }

    @Test
    void pausedRevealAccumulatesItsPausesAndCanStillComplete() {
        RoundStateSnapshot playing = machine.start(RoundStateSnapshot.preparing(track(0)), WINDOW);
        RoundStateSnapshot revealed = machine.voteSkip(playing, SOLO, START.plusSeconds(10), SOLO_ACTIVE);

        RoundStateSnapshot once = machine.resume(
                machine.pause(revealed, START.plusSeconds(11)), START.plusSeconds(31));
        RoundStateSnapshot pausedAgain = machine.pause(once, START.plusSeconds(32));

        assertThat(once.revealPause()).isEqualTo(Duration.ofSeconds(20));
        assertThat(once.scoringWindow()).contains(WINDOW);
        assertThat(machine.resume(pausedAgain, START.plusSeconds(37)).revealPause()).
                isEqualTo(Duration.ofSeconds(25));

        RoundStateSnapshot completed = machine.complete(pausedAgain, START.plusSeconds(40));
        assertThat(completed.status()).isEqualTo(RoundStatus.COMPLETED);
        assertThat(completed.paused()).isFalse();
        assertThat(completed.revealPause()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void pauseAndResumeRejectInvalidStatesAndTimes() {
        RoundStateSnapshot preparing = RoundStateSnapshot.preparing(track(0));
        RoundStateSnapshot playing = machine.start(preparing, WINDOW);
        RoundStateSnapshot revealed = machine.voteSkip(playing, SOLO, START.plusSeconds(10), SOLO_ACTIVE);

        assertFailure(
                () -> machine.pause(preparing, START),
                GameTransitionError.ILLEGAL_ROUND_TRANSITION);
        assertFailure(
                () -> machine.pause(playing, START.minusNanos(1)),
                GameTransitionError.ROUND_TIME_INVALID);
        assertFailure(
                () -> machine.pause(playing, WINDOW.deadline()),
                GameTransitionError.ROUND_TIME_INVALID);
        assertFailure(
                () -> machine.pause(revealed, START.plusSeconds(9)),
                GameTransitionError.ROUND_TIME_INVALID);
        assertFailure(
                () -> machine.resume(playing, START.plusSeconds(1)),
                GameTransitionError.ROUND_NOT_PAUSED);
        assertFailure(
                () -> machine.resume(machine.pause(playing, START.plusSeconds(5)), START.plusSeconds(4)),
                GameTransitionError.ROUND_TIME_INVALID);
    }

    @Test
    void snapshotRejectsDataThatDoesNotBelongToItsLifecycleState() {
        assertThatThrownBy(() -> new RoundStateSnapshot(
                        track(0),
                        RoundStatus.PREPARING,
                        java.util.Optional.of(WINDOW),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        RoundScoreState.unscored(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("round snapshot does not match its status");
    }

    private void assertFailure(Runnable action, GameTransitionError expected) {
        assertThatThrownBy(action::run).
                isInstanceOfSatisfying(GameTransitionException.class, failure -> {
                    assertThat(failure.error()).isEqualTo(expected);
                    assertThat(failure.code()).isEqualTo(expected.code());
                });
    }

    static GameTrackSnapshot track(int order) {
        return new GameTrackSnapshot(
                UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", order + 1)),
                order,
                new GameAnswerSnapshot(
                        "Title " + order, "Artist " + order, null, null, Map.of()),
                new GamePlaybackSnapshot(0, 180),
                new GameMediaSnapshot("tracks/" + order + "/audio.mp3", "checksum-" + order));
    }
}
