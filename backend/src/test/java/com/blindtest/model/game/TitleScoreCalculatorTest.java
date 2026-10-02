package com.blindtest.model.game;

import static com.blindtest.model.game.GameTestParticipants.SOLO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TitleScoreCalculatorTest {

    private static final Instant DEADLINE = Instant.parse("2026-08-04T12:01:00Z");
    private static final Duration ROUND_DURATION = Duration.ofSeconds(60);
    private static final RoundScoringWindow WINDOW =
            new RoundScoringWindow(DEADLINE, ROUND_DURATION);
    private static final AnswerMatchResult TITLE_MATCH =
            AnswerMatchResult.matched(GameAnswerType.TITLE);

    private final TitleScoreCalculator calculator = new TitleScoreCalculator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("scoreBoundaries")
    void calculatesApprovedLinearBoundaries(
            String scenario,
            Instant acceptedAt,
            int expectedPoints) {
        RoundScoreResult result = calculator.scoreAnswer(
                TITLE_MATCH, acceptedAt, WINDOW, SOLO, RoundScoreState.unscored());

        assertThat(result.pointsAwarded()).as(scenario).isEqualTo(expectedPoints);
        assertThat(result.state().totalPoints()).isEqualTo(expectedPoints);
        assertThat(result.awardedNow()).isTrue();
    }

    @Test
    void clampsAnswersAcceptedBeforeTheNominalStartToMaximumPoints() {
        RoundScoreResult result = calculator.scoreAnswer(
                TITLE_MATCH,
                WINDOW.startsAt().minusSeconds(30),
                WINDOW,
                SOLO,
                RoundScoreState.unscored());

        assertThat(result.pointsAwarded()).isEqualTo(1000);
    }

    @Test
    void roundsPositiveHalfValuesUpUsingExactIntegerArithmetic() {
        RoundScoringWindow nanosecondWindow =
                new RoundScoringWindow(DEADLINE, Duration.ofNanos(2000));

        RoundScoreResult result = calculator.scoreAnswer(
                TITLE_MATCH,
                DEADLINE.minusNanos(1001),
                nanosecondWindow,
                SOLO,
                RoundScoreState.unscored());

        assertThat(result.pointsAwarded()).isEqualTo(501);
    }

    @Test
    void immediatelyBeforeDeadlineReceivesTheMinimumScore() {
        RoundScoreResult result = calculator.scoreAnswer(
                TITLE_MATCH,
                DEADLINE.minusNanos(1),
                WINDOW,
                SOLO,
                RoundScoreState.unscored());

        assertThat(result.pointsAwarded()).isEqualTo(100);
    }

    @Test
    void deadlineAndLaterAnswersAwardZeroWithoutConsumingTheTitleAward() {
        RoundScoreState initial = RoundScoreState.unscored();

        RoundScoreResult atDeadline =
                calculator.scoreAnswer(TITLE_MATCH, DEADLINE, WINDOW, SOLO, initial);
        RoundScoreResult afterDeadline =
                calculator.scoreAnswer(TITLE_MATCH, DEADLINE.plusNanos(1), WINDOW, SOLO, initial);

        assertThat(atDeadline).isEqualTo(new RoundScoreResult(0, initial));
        assertThat(afterDeadline).isEqualTo(new RoundScoreResult(0, initial));
        assertThat(atDeadline.state().titleAwarded()).isFalse();
    }

    @Test
    void secondaryAndRejectedAnswersAlwaysAwardZero() {
        for (GameAnswerType secondary : new GameAnswerType[] {
                GameAnswerType.ARTIST, GameAnswerType.COMPOSER, GameAnswerType.ALBUM}) {
            RoundScoreResult result = calculator.scoreAnswer(
                    AnswerMatchResult.matched(secondary),
                    WINDOW.startsAt(),
                    WINDOW,
                    SOLO,
                    RoundScoreState.unscored());
            assertThat(result.pointsAwarded()).as(secondary.name()).isZero();
            assertThat(result.state().titleAwarded()).isFalse();
        }

        RoundScoreResult rejected = calculator.scoreAnswer(
                AnswerMatchResult.noMatch(),
                WINDOW.startsAt(),
                WINDOW,
                SOLO,
                RoundScoreState.unscored());
        assertThat(rejected.pointsAwarded()).isZero();
    }

    @Test
    void duplicateCorrectTitlesCannotAwardTwice() {
        RoundScoreResult first = calculator.scoreAnswer(
                TITLE_MATCH,
                WINDOW.startsAt().plusSeconds(15),
                WINDOW,
                SOLO,
                RoundScoreState.unscored());

        RoundScoreResult duplicate = calculator.scoreAnswer(
                TITLE_MATCH,
                WINDOW.startsAt().plusSeconds(20),
                WINDOW,
                SOLO,
                first.state());

        assertThat(first.pointsAwarded()).isEqualTo(750);
        assertThat(duplicate.pointsAwarded()).isZero();
        assertThat(duplicate.state()).isSameAs(first.state());
        assertThat(duplicate.state().totalPoints()).isEqualTo(750);
    }

    @Test
    void skipAndTimeoutAlwaysAwardZeroAndPreserveExistingState() {
        RoundScoreState awarded = RoundScoreState.unscored().withTitleAward(SOLO, 640);

        RoundScoreResult skip = calculator.scoreSkip(awarded);
        RoundScoreResult timeout = calculator.scoreTimeout(awarded);

        assertThat(skip).isEqualTo(new RoundScoreResult(0, awarded));
        assertThat(timeout).isEqualTo(new RoundScoreResult(0, awarded));
        assertThat(skip.state()).isSameAs(awarded);
        assertThat(timeout.state()).isSameAs(awarded);
    }

    @Test
    void arithmeticRemainsExactForDurationsWhoseNanosecondsOverflowLong() {
        Duration thousandYears = Duration.ofDays(365_000);
        RoundScoringWindow longWindow = new RoundScoringWindow(DEADLINE, thousandYears);

        RoundScoreResult result = calculator.scoreAnswer(
                TITLE_MATCH,
                DEADLINE.minus(thousandYears.dividedBy(2)),
                longWindow,
                SOLO,
                RoundScoreState.unscored());

        assertThatThrownBy(thousandYears::toNanos).isInstanceOf(ArithmeticException.class);
        assertThat(result.pointsAwarded()).isEqualTo(500);
    }

    @Test
    void stateAndResultsExposeTheStableScoringRuleVersion() {
        RoundScoreResult result = calculator.scoreAnswer(
                TITLE_MATCH, WINDOW.startsAt(), WINDOW, SOLO, RoundScoreState.unscored());

        assertThat(TitleScoreCalculator.SCORING_RULE_VERSION).isEqualTo("title-linear-v1");
        assertThat(result.scoringRuleVersion()).isEqualTo("title-linear-v1");
        assertThat(result.state().scoringRuleVersion()).isEqualTo("title-linear-v1");
    }

    @Test
    void rejectsInvalidWindowsStatesAndResults() {
        assertThatThrownBy(() -> new RoundScoringWindow(DEADLINE, Duration.ZERO)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("configuredDuration must be positive");
        assertThatThrownBy(() -> RoundScoreState.unscored().withTitleAward(SOLO, 99)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("awardedTitlePoints is outside the scoring range");
        assertThatThrownBy(() -> new RoundScoreResult(100, RoundScoreState.unscored())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("a positive award must be retained in state");
    }

    @Test
    void rejectsMissingScoringInputs() {
        assertThatThrownBy(() -> calculator.scoreAnswer(
                        null, WINDOW.startsAt(), WINDOW, SOLO, RoundScoreState.unscored())).
                isInstanceOf(NullPointerException.class).
                hasMessage("match is required");
        assertThatThrownBy(() -> calculator.scoreAnswer(
                        TITLE_MATCH, null, WINDOW, SOLO, RoundScoreState.unscored())).
                isInstanceOf(NullPointerException.class).
                hasMessage("acceptedAt is required");
        assertThatThrownBy(() -> calculator.scoreSkip(null)).
                isInstanceOf(NullPointerException.class).
                hasMessage("state is required");
    }

    private static Stream<Arguments> scoreBoundaries() {
        return Stream.of(
                Arguments.of("start", DEADLINE.minusSeconds(60), 1000),
                Arguments.of("midpoint", DEADLINE.minusSeconds(30), 500),
                Arguments.of("floor threshold", DEADLINE.minusSeconds(6), 100),
                Arguments.of("below floor threshold", DEADLINE.minusMillis(5999), 100));
    }
}
