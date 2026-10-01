package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    void omittedDifficultySelectionIncludesEveryTrack() {
        GameConfiguration configuration = validator.validate(
                request(5, 30), new PlaylistEligibilitySnapshot(PLAYLIST_ID, 5));

        assertThat(configuration.difficultyFilter()).isEqualTo(DifficultyFilter.ALL);
    }

    @Test
    void keepsTheRequestedDifficultyFilterWhenEnoughTracksMatch() {
        PlaylistEligibilitySnapshot eligibility = new PlaylistEligibilitySnapshot(
                PLAYLIST_ID, 9, Map.of(PlaylistEligibilitySnapshot.UNRATED, 2, 1, 3, 4, 2, 5, 2));

        GameConfiguration configuration = validator.validate(
                difficultyRequest(5, List.of(1, 4), true), eligibility);

        assertThat(configuration.difficultyFilter()).
                isEqualTo(new DifficultyFilter(Set.of(1, 4), true));
    }

    @Test
    void rejectsDifficultyFiltersMatchingTooFewTracks() {
        PlaylistEligibilitySnapshot eligibility = new PlaylistEligibilitySnapshot(
                PLAYLIST_ID, 9, Map.of(PlaylistEligibilitySnapshot.UNRATED, 2, 1, 3, 4, 2, 5, 2));

        assertThatThrownBy(() -> validator.validate(
                difficultyRequest(6, List.of(1, 4), false), eligibility)).
                isInstanceOfSatisfying(GameConfigurationValidationException.class, exception ->
                        assertThat(exception.code()).
                                isEqualTo("GAME_DIFFICULTY_INSUFFICIENT_TRACKS"));
    }

    @ParameterizedTest
    @MethodSource("invalidDifficultySelections")
    void rejectsEmptyOrOutOfRangeDifficultySelections(List<Integer> levels, boolean unrated) {
        assertThatThrownBy(() -> validator.validate(
                difficultyRequest(5, levels, unrated),
                new PlaylistEligibilitySnapshot(PLAYLIST_ID, 10))).
                isInstanceOfSatisfying(GameConfigurationValidationException.class, exception ->
                        assertThat(exception.code()).isEqualTo("GAME_DIFFICULTY_INVALID"));
    }

    static Stream<Arguments> invalidDifficultySelections() {
        return Stream.of(
                Arguments.of(List.of(), false),
                Arguments.of(List.of(0, 3), true),
                Arguments.of(List.of(6), true),
                Arguments.of(Arrays.asList(2, null), true));
    }

    private RequestedGameConfiguration difficultyRequest(
            int trackCount, List<Integer> levels, boolean includeUnrated) {
        return new RequestedGameConfiguration(
                PLAYLIST_ID, trackCount, 30, Map.of(), levels, includeUnrated);
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
