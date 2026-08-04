package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoundPlaybackWindowTest {

    private static final Instant START = Instant.parse("2026-08-04T12:00:00Z");

    @Test
    void appliesSnapshottedOffsetForTheConfiguredRoundDuration() {
        RoundPlaybackWindow window = RoundPlaybackWindow.from(
                new GamePlaybackSnapshot(42, 180), scoringWindow(60));

        assertThat(window.startsAt()).isEqualTo(START);
        assertThat(window.deadline()).isEqualTo(START.plusSeconds(60));
        assertThat(window.excerptStartSeconds()).isEqualTo(42);
        assertThat(window.excerptEndSeconds()).isEqualTo(102);
    }

    @Test
    void zeroOffsetStartsAtBeginningEvenWhenMediaDurationIsUnknown() {
        RoundPlaybackWindow window = RoundPlaybackWindow.from(
                new GamePlaybackSnapshot(0, null), scoringWindow(30));

        assertThat(window.excerptStartSeconds()).isZero();
        assertThat(window.excerptEndSeconds()).isEqualTo(30);
    }

    @Test
    void urlMustCoverRoundAndReconnectBufferExactly() {
        RoundPlaybackWindow window = RoundPlaybackWindow.from(
                new GamePlaybackSnapshot(10, 180), scoringWindow(60));

        assertThat(window.minimumUrlExpiresAt()).isEqualTo(START.plusSeconds(90));
        assertThat(window.isCoveredByUrlExpiringAt(START.plusSeconds(90))).isTrue();
        assertThat(window.isCoveredByUrlExpiringAt(
                START.plusSeconds(90).minusNanos(1))).isFalse();
    }

    @Test
    void rejectsFractionalRoundDurationsAndInconsistentHandcraftedMetadata() {
        RoundScoringWindow fractional = new RoundScoringWindow(
                START.plusSeconds(30).plusNanos(1),
                Duration.ofSeconds(30).plusNanos(1));

        assertThatThrownBy(() -> RoundPlaybackWindow.from(
                        new GamePlaybackSnapshot(0, null), fractional)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("round duration must use whole seconds");
        assertThatThrownBy(() -> new RoundPlaybackWindow(
                        START,
                        START.plusSeconds(30).plusNanos(1),
                        10,
                        40,
                        START.plusSeconds(60).plusNanos(1))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("round duration must use whole seconds");
        assertThatThrownBy(() -> new RoundPlaybackWindow(
                        START,
                        START.plusSeconds(30),
                        10,
                        41,
                        START.plusSeconds(60))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("excerpt duration must match round duration");
    }

    private RoundScoringWindow scoringWindow(int durationSeconds) {
        return new RoundScoringWindow(
                START.plusSeconds(durationSeconds), Duration.ofSeconds(durationSeconds));
    }
}
