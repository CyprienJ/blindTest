package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecondaryDiscoveryTrackerTest {

    private static final GameParticipantId FIRST = participant(1);
    private static final GameParticipantId SECOND = participant(2);

    private final SecondaryDiscoveryTracker tracker = new SecondaryDiscoveryTracker();

    @Test
    void recordsASecondaryDiscoveryOnlyForTheSubmittingParticipant() {
        SecondaryDiscoveryState initial = SecondaryDiscoveryState.empty();

        SecondaryDiscoveryResult result = tracker.route(
                initial, FIRST, AnswerMatchResult.matched(GameAnswerType.ARTIST));

        assertThat(result.outcome()).isEqualTo(SecondaryDiscoveryOutcome.DISCOVERED);
        assertThat(result.discoveredType()).contains(GameAnswerType.ARTIST);
        assertThat(result.state().projectFor(FIRST).discoveredTypes()).
                containsExactly(GameAnswerType.ARTIST);
        assertThat(result.state().projectFor(SECOND).discoveredTypes()).isEmpty();
        assertThat(initial.projectFor(FIRST).discoveredTypes()).isEmpty();
    }

    @Test
    void duplicateReturnsAlreadyFoundWithoutDisclosingAType() {
        SecondaryDiscoveryState discovered = tracker.route(
                SecondaryDiscoveryState.empty(),
                FIRST,
                AnswerMatchResult.matched(GameAnswerType.COMPOSER)).state();

        SecondaryDiscoveryResult duplicate = tracker.route(
                discovered, FIRST, AnswerMatchResult.matched(GameAnswerType.COMPOSER));

        assertThat(duplicate.outcome()).isEqualTo(SecondaryDiscoveryOutcome.ALREADY_FOUND);
        assertThat(duplicate.discoveredType()).isEmpty();
        assertThat(duplicate.state()).isSameAs(discovered);
    }

    @Test
    void anotherParticipantCanIndependentlyDiscoverTheSameCategory() {
        SecondaryDiscoveryState firstDiscovered = tracker.route(
                SecondaryDiscoveryState.empty(),
                FIRST,
                AnswerMatchResult.matched(GameAnswerType.ALBUM)).state();

        SecondaryDiscoveryResult secondResult = tracker.route(
                firstDiscovered, SECOND, AnswerMatchResult.matched(GameAnswerType.ALBUM));

        assertThat(secondResult.outcome()).isEqualTo(SecondaryDiscoveryOutcome.DISCOVERED);
        assertThat(secondResult.discoveredType()).contains(GameAnswerType.ALBUM);
        assertThat(secondResult.state().projectFor(FIRST).discoveredTypes()).
                containsExactly(GameAnswerType.ALBUM);
        assertThat(secondResult.state().projectFor(SECOND).discoveredTypes()).
                containsExactly(GameAnswerType.ALBUM);
    }

    @Test
    void titleRemainsAvailableAfterSecondaryDiscoveryWithoutChangingState() {
        SecondaryDiscoveryState discovered = tracker.route(
                SecondaryDiscoveryState.empty(),
                FIRST,
                AnswerMatchResult.matched(GameAnswerType.ARTIST)).state();

        SecondaryDiscoveryResult title = tracker.route(
                discovered, FIRST, AnswerMatchResult.matched(GameAnswerType.TITLE));

        assertThat(title.outcome()).isEqualTo(SecondaryDiscoveryOutcome.TITLE_AVAILABLE);
        assertThat(title.discoveredType()).isEmpty();
        assertThat(title.state()).isSameAs(discovered);
    }

    @Test
    void secondaryAndRejectedGuessesDoNotChangeScoreOrRoundState() {
        RoundStateSnapshot round = RoundStateSnapshot.preparing(
                RoundStateMachineTest.track(0));
        SecondaryDiscoveryState discoveries = SecondaryDiscoveryState.empty();

        SecondaryDiscoveryResult secondary = tracker.route(
                discoveries, FIRST, AnswerMatchResult.matched(GameAnswerType.ARTIST));
        SecondaryDiscoveryResult rejected = tracker.route(
                discoveries, FIRST, AnswerMatchResult.noMatch());

        assertThat(secondary.outcome()).isEqualTo(SecondaryDiscoveryOutcome.DISCOVERED);
        assertThat(rejected.outcome()).isEqualTo(SecondaryDiscoveryOutcome.NO_MATCH);
        assertThat(rejected.state()).isSameAs(discoveries);
        assertThat(round).isEqualTo(RoundStateSnapshot.preparing(round.track()));
        assertThat(round.scoreState().totalPoints()).isZero();
    }

    @Test
    void projectionsAndResultShapesCannotExposeInvalidDiscoveryData() {
        ParticipantDiscoveryProjection projection = new ParticipantDiscoveryProjection(
                Set.of(GameAnswerType.ARTIST));

        assertThatThrownBy(() -> projection.discoveredTypes().add(GameAnswerType.ALBUM)).
                isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ParticipantDiscoveryProjection(
                        Set.of(GameAnswerType.TITLE))).
                isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecondaryDiscoveryResult(
                        SecondaryDiscoveryOutcome.ALREADY_FOUND,
                        Optional.of(GameAnswerType.ARTIST),
                        SecondaryDiscoveryState.empty())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("only a new discovery can disclose its type");
    }

    private static GameParticipantId participant(int suffix) {
        return new GameParticipantId(UUID.fromString(
                "00000000-0000-0000-0000-" + String.format("%012d", suffix)));
    }
}
