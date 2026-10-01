package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameTrackSelectorTest {

    private static final UUID PLAYLIST_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Test
    void sameSeedProducesSameSelectionAndPlayOrder() {
        List<GameTrackCandidate> candidates = candidates(10);

        GameTrackSelection first = selectorWithSeed(4872).select(configuration(5), candidates);
        GameTrackSelection second = selectorWithSeed(4872).select(configuration(5), candidates);

        assertThat(first).isEqualTo(second);
        assertThat(first.tracks()).extracting(GameTrackSnapshot::playOrder).
                containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void selectsExactRequestedCountWithoutDuplicateIdentities() {
        List<GameTrackCandidate> candidates = candidates(12);

        GameTrackSelection selection = selectorWithSeed(92).select(configuration(7), candidates);

        assertThat(selection.tracks()).hasSize(7);
        assertThat(selection.tracks()).extracting(GameTrackSnapshot::trackId).
                doesNotHaveDuplicates();
    }

    @Test
    void ignoresIneligibleTracksAndDuplicatePlaylistIdentities() {
        List<GameTrackCandidate> candidates = new ArrayList<>(candidates(7));
        GameTrackCandidate eligibleDuplicate = candidates.get(0);
        candidates.add(eligibleDuplicate);
        candidates.add(candidate(99, false));

        GameTrackSelection selection = selectorWithSeed(13).select(configuration(7), candidates);

        assertThat(selection.tracks()).extracting(GameTrackSnapshot::trackId).
                doesNotContain(candidateId(99)).
                doesNotHaveDuplicates().
                containsExactlyInAnyOrderElementsOf(
                        candidates.subList(0, 7).stream().
                                map(GameTrackCandidate::trackId).
                                toList());
    }

    @Test
    void fullSelectionShufflesTheWholeEligiblePlaylist() {
        List<GameTrackCandidate> candidates = candidates(6);

        GameTrackSelection selection = selectorWithSeed(1).select(configuration(6), candidates);

        assertThat(selection.tracks()).extracting(GameTrackSnapshot::trackId).
                containsExactlyInAnyOrderElementsOf(
                        candidates.stream().map(GameTrackCandidate::trackId).toList()).
                isNotEqualTo(candidates.stream().map(GameTrackCandidate::trackId).toList());
    }

    @Test
    void insufficientDistinctEligibleTracksFailsWithStableConfigurationError() {
        List<GameTrackCandidate> candidates = new ArrayList<>(candidates(4));
        candidates.add(candidates.get(0));
        candidates.add(candidate(99, false));

        assertThatThrownBy(() -> selectorWithSeed(4).select(configuration(5), candidates)).
                isInstanceOfSatisfying(GameConfigurationValidationException.class, failure -> {
                    assertThat(failure.error()).
                            isEqualTo(GameConfigurationError.PLAYLIST_INSUFFICIENT_TRACKS);
                    assertThat(failure.code()).isEqualTo("GAME_PLAYLIST_INSUFFICIENT_TRACKS");
                });
    }

    @Test
    void selectedAnswersAndMediaStayDetachedFromLaterCatalogueEdits() {
        List<String> titleAliases = new ArrayList<>(List.of("Original alias"));
        Map<GameAnswerType, List<String>> aliases = new EnumMap<>(GameAnswerType.class);
        aliases.put(GameAnswerType.TITLE, titleAliases);
        GameAnswerSnapshot originalAnswers =
                new GameAnswerSnapshot("Original title", "Original artist", null, null, aliases);
        GameTrackCandidate original = new GameTrackCandidate(
                candidateId(1),
                true,
                originalAnswers,
                new GamePlaybackSnapshot(12, 180),
                new GameMediaSnapshot("tracks/1/audio-old.mp3", "checksum-old"));

        GameTrackSelection selection = selectorWithSeed(5).select(
                configuration(1), List.of(original));
        titleAliases.set(0, "Edited alias");
        GameTrackCandidate editedCatalogueValue = new GameTrackCandidate(
                candidateId(1),
                true,
                new GameAnswerSnapshot(
                        "Edited title", "Edited artist", "Edited composer", "Edited album", Map.of()),
                new GamePlaybackSnapshot(0, 240),
                new GameMediaSnapshot("tracks/1/audio-new.mp3", "checksum-new"));

        GameTrackSnapshot snapshot = selection.tracks().getFirst();
        assertThat(editedCatalogueValue.answers().title()).isEqualTo("Edited title");
        assertThat(snapshot.answers().title()).isEqualTo("Original title");
        assertThat(snapshot.answers().aliases().get(GameAnswerType.TITLE)).
                containsExactly("Original alias");
        assertThat(snapshot.playback()).isEqualTo(new GamePlaybackSnapshot(12, 180));
        assertThat(snapshot.media()).
                isEqualTo(new GameMediaSnapshot("tracks/1/audio-old.mp3", "checksum-old"));
        assertThatThrownBy(() -> snapshot.answers().aliases().get(GameAnswerType.TITLE).add("x")).
                isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void seededSamplingCanReachEveryEligibleTrack() {
        Set<UUID> observed = new HashSet<>();
        List<GameTrackCandidate> candidates = candidates(6);

        for (int seed = 0; seed < 250; seed++) {
            List<GameTrackSnapshot> selected =
                    selectorWithSeed(seed).select(configuration(5), candidates).tracks();
            assertThat(selected).hasSize(5);
            assertThat(selected).extracting(GameTrackSnapshot::trackId).doesNotHaveDuplicates();
            selected.stream().
                    map(GameTrackSnapshot::trackId).
                    forEach(observed::add);
        }

        assertThat(observed).containsExactlyInAnyOrderElementsOf(
                candidates.stream().map(GameTrackCandidate::trackId).toList());
    }

    @Test
    void rejectsRandomValuesOutsideTheRequestedBound() {
        GameTrackSelector selector = new GameTrackSelector(bound -> bound);

        assertThatThrownBy(() -> selector.select(configuration(5), candidates(6))).
                isInstanceOf(IllegalStateException.class).
                hasMessage("game random returned a value outside its bound");
    }

    @Test
    void selectsOnlyTracksIncludedByTheDifficultyFilter() {
        List<GameTrackCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            candidates.add(withDifficulty(candidate(index, true), index < 6 ? index % 3 + 1 : null));
        }
        GameConfiguration configuration = new GameConfiguration(
                PLAYLIST_ID, 4, 30, new DifficultyFilter(Set.of(1, 2), false));

        GameTrackSelection selection = selectorWithSeed(7).select(configuration, candidates);

        assertThat(selection.tracks()).extracting(GameTrackSnapshot::trackId).
                containsExactlyInAnyOrder(
                        candidateId(0), candidateId(1), candidateId(3), candidateId(4));
    }

    @Test
    void difficultyFilterLeavingTooFewTracksFailsWithStableConfigurationError() {
        List<GameTrackCandidate> candidates = new ArrayList<>(candidates(6));
        GameConfiguration configuration = new GameConfiguration(
                PLAYLIST_ID, 5, 30, new DifficultyFilter(Set.of(3), false));

        assertThatThrownBy(() -> selectorWithSeed(1).select(configuration, candidates)).
                isInstanceOfSatisfying(GameConfigurationValidationException.class, exception ->
                        assertThat(exception.code()).
                                isEqualTo("GAME_DIFFICULTY_INSUFFICIENT_TRACKS"));
    }

    private GameTrackCandidate withDifficulty(GameTrackCandidate candidate, Integer difficulty) {
        return new GameTrackCandidate(
                candidate.trackId(), candidate.eligiblePublished(), candidate.answers(),
                candidate.playback(), candidate.media(), difficulty);
    }

    private GameTrackSelector selectorWithSeed(long seed) {
        Random random = new Random(seed);
        return new GameTrackSelector(random::nextInt);
    }

    private GameConfiguration configuration(int requestedTrackCount) {
        return new GameConfiguration(PLAYLIST_ID, requestedTrackCount, 30);
    }

    private List<GameTrackCandidate> candidates(int count) {
        List<GameTrackCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            candidates.add(candidate(index, true));
        }
        return candidates;
    }

    private GameTrackCandidate candidate(int index, boolean eligiblePublished) {
        return new GameTrackCandidate(
                candidateId(index),
                eligiblePublished,
                new GameAnswerSnapshot(
                        "Title " + index,
                        "Artist " + index,
                        "Composer " + index,
                        "Album " + index,
                        Map.of(GameAnswerType.TITLE, List.of("Alias " + index))),
                new GamePlaybackSnapshot(index, 180),
                new GameMediaSnapshot(
                        "tracks/" + index + "/audio.mp3", "checksum-" + index));
    }

    private UUID candidateId(int index) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", index));
    }
}
