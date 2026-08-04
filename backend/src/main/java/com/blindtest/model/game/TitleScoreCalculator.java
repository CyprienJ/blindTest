package com.blindtest.model.game;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Pure implementation of the V1 linear title-scoring rule from ADR-0007. */
public final class TitleScoreCalculator {

    public static final String SCORING_RULE_VERSION = "title-linear-v1";
    public static final int MAXIMUM_TITLE_POINTS = 1000;
    public static final int MINIMUM_TITLE_POINTS = 100;

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger MAXIMUM_POINTS = BigInteger.valueOf(MAXIMUM_TITLE_POINTS);

    /** Scores a typed answer accepted at the server timestamp and updates idempotency state. */
    public RoundScoreResult scoreAnswer(
            AnswerMatchResult match,
            Instant acceptedAt,
            RoundScoringWindow window,
            RoundScoreState state) {
        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(acceptedAt, "acceptedAt is required");
        Objects.requireNonNull(window, "window is required");
        Objects.requireNonNull(state, "state is required");

        if (state.titleAwarded()
                || match.matchedType().filter(type -> type == GameAnswerType.TITLE).isEmpty()
                || !acceptedAt.isBefore(window.deadline())) {
            return RoundScoreResult.noAward(state);
        }

        BigInteger durationNanos = toNanos(window.configuredDuration());
        BigInteger remainingNanos = toNanos(Duration.between(acceptedAt, window.deadline()));
        BigInteger clampedRemaining = remainingNanos.min(durationNanos).max(BigInteger.ZERO);
        int roundedLinearScore = roundHalfUp(
                MAXIMUM_POINTS.multiply(clampedRemaining), durationNanos);
        int awardedPoints = Math.max(MINIMUM_TITLE_POINTS, roundedLinearScore);
        return RoundScoreResult.titleAward(awardedPoints);
    }

    /** A skip never awards points or changes an existing title award. */
    public RoundScoreResult scoreSkip(RoundScoreState state) {
        return RoundScoreResult.noAward(Objects.requireNonNull(state, "state is required"));
    }

    /** A timeout never awards points or changes an existing title award. */
    public RoundScoreResult scoreTimeout(RoundScoreState state) {
        return RoundScoreResult.noAward(Objects.requireNonNull(state, "state is required"));
    }

    private int roundHalfUp(BigInteger numerator, BigInteger denominator) {
        BigInteger doubledDenominator = denominator.multiply(TWO);
        return numerator.multiply(TWO).add(denominator).
                divide(doubledDenominator).intValueExact();
    }

    private BigInteger toNanos(Duration duration) {
        return BigInteger.valueOf(duration.getSeconds()).multiply(NANOS_PER_SECOND).
                add(BigInteger.valueOf(duration.getNano()));
    }
}
