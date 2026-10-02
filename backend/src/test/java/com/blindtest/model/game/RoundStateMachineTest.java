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
