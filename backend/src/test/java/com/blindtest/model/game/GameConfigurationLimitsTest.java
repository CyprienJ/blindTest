package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GameConfigurationLimitsTest {

    private static final UUID PLAYLIST_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111");
    private static final PlaylistEligibilitySnapshot ELIGIBLE_PLAYLIST =
            new PlaylistEligibilitySnapshot(
                    PLAYLIST_ID, GameConfigurationLimits.MAXIMUM_TRACK_COUNT);

    private final GameConfigurationValidator validator = new GameConfigurationValidator();

    @Test
    void exposesTheApprovedDefaultsAndIncrements() {
        assertThat(GameConfigurationLimits.DEFAULT_TRACK_COUNT).isEqualTo(15);
        assertThat(GameConfigurationLimits.TRACK_COUNT_INCREMENT).isOne();
        assertThat(GameConfigurationLimits.DEFAULT_ROUND_DURATION_SECONDS).isEqualTo(30);
        assertThat(GameConfigurationLimits.ROUND_DURATION_INCREMENT_SECONDS).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 15, 30})
    void acceptsTrackCountBoundariesAndDefault(int trackCount) {
        GameConfiguration result = validator.validate(
                request(trackCount, GameConfigurationLimits.DEFAULT_ROUND_DURATION_SECONDS),
                ELIGIBLE_PLAYLIST);

        assertThat(result.requestedTrackCount()).isEqualTo(trackCount);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 31, Integer.MAX_VALUE})
    void rejectsTrackCountsOutsideTheApprovedRange(int trackCount) {
        assertError(
                request(trackCount, GameConfigurationLimits.DEFAULT_ROUND_DURATION_SECONDS),
                GameConfigurationError.TRACK_COUNT_INVALID);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 30, 60})
    void acceptsRoundDurationBoundariesAndDefault(int durationSeconds) {
        GameConfiguration result = validator.validate(
                request(GameConfigurationLimits.MINIMUM_TRACK_COUNT, durationSeconds),
                ELIGIBLE_PLAYLIST);

        assertThat(result.maximumRoundDurationSeconds()).isEqualTo(durationSeconds);
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 11, 61, Integer.MAX_VALUE})
    void rejectsRoundDurationsOutsideTheRangeOrIncrement(int durationSeconds) {
        assertError(
                request(GameConfigurationLimits.MINIMUM_TRACK_COUNT, durationSeconds),
                GameConfigurationError.ROUND_DURATION_INVALID);
    }

    private RequestedGameConfiguration request(int trackCount, int durationSeconds) {
        return new RequestedGameConfiguration(
                PLAYLIST_ID, trackCount, durationSeconds, Map.of());
    }

    private void assertError(
            RequestedGameConfiguration request, GameConfigurationError expectedError) {
        assertThatThrownBy(() -> validator.validate(request, ELIGIBLE_PLAYLIST)).
                isInstanceOf(GameConfigurationValidationException.class).
                satisfies(error -> assertThat(
                        ((GameConfigurationValidationException) error).error()).
                        isEqualTo(expectedError));
    }
}
