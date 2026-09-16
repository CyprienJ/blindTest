package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GameConfigurationValidatorTest {

    private static final UUID PLAYLIST_ID = UUID.fromString(
            "11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_PLAYLIST_ID = UUID.fromString(
            "22222222-2222-4222-8222-222222222222");

    private final GameConfigurationValidator validator = new GameConfigurationValidator();

    @Test
    void createsAnImmutableSnapshotWhenThePlaylistHasExactlyEnoughTracks() {
        RequestedGameConfiguration requested = request(5, 30);
        PlaylistEligibilitySnapshot eligibility = new PlaylistEligibilitySnapshot(PLAYLIST_ID, 5);

        GameConfiguration configuration = validator.validate(requested, eligibility);

        assertThat(configuration.playlistId()).isEqualTo(PLAYLIST_ID);
        assertThat(configuration.requestedTrackCount()).isEqualTo(5);
        assertThat(configuration.maximumRoundDurationSeconds()).isEqualTo(30);
        assertThat(configuration.extensions()).isEmpty();
        assertThatThrownBy(() -> configuration.extensions().put("mode", "ordered")).
                isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatedConfigurationDoesNotRetainThePlaylistEligibilitySnapshot() {
        PlaylistEligibilitySnapshot original = new PlaylistEligibilitySnapshot(PLAYLIST_ID, 12);
        GameConfiguration configuration = validator.validate(request(8, 45), original);

        PlaylistEligibilitySnapshot afterPlaylistEdit =
                new PlaylistEligibilitySnapshot(PLAYLIST_ID, 0);

        assertThat(afterPlaylistEdit.eligiblePublishedTrackCount()).isZero();
        assertThat(configuration.requestedTrackCount()).isEqualTo(8);
        assertThat(configuration.maximumRoundDurationSeconds()).isEqualTo(45);
    }

    @Test
    void requestedExtensionShapeIsDefensivelyCopied() {
        Map<String, String> extensions = new HashMap<>();
        RequestedGameConfiguration requested =
                new RequestedGameConfiguration(PLAYLIST_ID, 5, 30, extensions);

        extensions.put("futureMode", "ordered");

        assertThat(requested.extensions()).isEmpty();
        assertThatThrownBy(() -> requested.extensions().put("mode", "ordered")).
                isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void playlistEligibilityProjectionRejectsMissingIdentity() {
        assertThatThrownBy(() -> new PlaylistEligibilitySnapshot(null, 1)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("playlistId is required");
    }

    @Test
    void playlistEligibilityProjectionRejectsNegativeCounts() {
        assertThatThrownBy(() -> new PlaylistEligibilitySnapshot(PLAYLIST_ID, -1)).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("eligiblePublishedTrackCount cannot be negative");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigurations")
    void rejectsInvalidConfigurationsWithStableDomainErrors(
            String scenario,
            RequestedGameConfiguration requested,
            PlaylistEligibilitySnapshot eligibility,
            GameConfigurationError expectedError) {
        assertThatThrownBy(() -> validator.validate(requested, eligibility)).
                isInstanceOf(GameConfigurationValidationException.class).
                satisfies(error -> {
                    GameConfigurationValidationException validationError =
                            (GameConfigurationValidationException) error;
                    assertThat(validationError.error()).isEqualTo(expectedError);
                    assertThat(validationError.code()).isEqualTo(expectedError.code());
                    assertThat(validationError.getMessage()).isEqualTo(expectedError.safeMessage());
                });
    }

    private static Stream<Arguments> invalidConfigurations() {
        PlaylistEligibilitySnapshot eligible = new PlaylistEligibilitySnapshot(PLAYLIST_ID, 10);
        return Stream.of(
                Arguments.of("missing configuration", null, eligible,
                        GameConfigurationError.CONFIGURATION_REQUIRED),
                Arguments.of("missing playlist", new RequestedGameConfiguration(
                                null, 5, 30, Map.of()), eligible,
                        GameConfigurationError.PLAYLIST_REQUIRED),
                Arguments.of("non-positive track count", request(0, 30), eligible,
                        GameConfigurationError.TRACK_COUNT_INVALID),
                Arguments.of("non-positive duration", request(5, 0), eligible,
                        GameConfigurationError.ROUND_DURATION_INVALID),
                Arguments.of("unsupported extension", new RequestedGameConfiguration(
                                PLAYLIST_ID, 5, 30, Map.of("mode", "ordered")), eligible,
                        GameConfigurationError.EXTENSION_UNSUPPORTED),
                Arguments.of("unavailable playlist", request(5, 30), null,
                        GameConfigurationError.PLAYLIST_NOT_FOUND),
                Arguments.of("different playlist projection", request(5, 30),
                        new PlaylistEligibilitySnapshot(OTHER_PLAYLIST_ID, 10),
                        GameConfigurationError.PLAYLIST_NOT_FOUND),
                Arguments.of("playlist without eligible tracks", request(5, 30),
                        new PlaylistEligibilitySnapshot(PLAYLIST_ID, 0),
                        GameConfigurationError.PLAYLIST_INELIGIBLE),
                Arguments.of("insufficient eligible tracks", request(6, 30),
                        new PlaylistEligibilitySnapshot(PLAYLIST_ID, 5),
                        GameConfigurationError.PLAYLIST_INSUFFICIENT_TRACKS));
    }

    private static RequestedGameConfiguration request(int trackCount, int durationSeconds) {
        return new RequestedGameConfiguration(
                PLAYLIST_ID, trackCount, durationSeconds, Map.of());
    }
}
