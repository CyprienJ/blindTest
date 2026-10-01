package com.blindtest.model.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Uniformly selects eligible playlist tracks and freezes their game-facing metadata. */
public final class GameTrackSelector {

    private final GameRandom random;

    public GameTrackSelector(GameRandom random) {
        this.random = Objects.requireNonNull(random, "random is required");
    }

    /** Selects the configured count without replacement and shuffles the resulting play order. */
    public GameTrackSelection select(
            GameConfiguration configuration,
            List<GameTrackCandidate> playlistTracks) {
        Objects.requireNonNull(configuration, "configuration is required");
        Objects.requireNonNull(playlistTracks, "playlistTracks are required");

        List<GameTrackCandidate> eligibleTracks = distinctEligibleTracks(playlistTracks);
        int requestedCount = configuration.requestedTrackCount();
        if (eligibleTracks.size() < requestedCount) {
            throw new GameConfigurationValidationException(
                    GameConfigurationError.PLAYLIST_INSUFFICIENT_TRACKS);
        }
        DifficultyFilter difficultyFilter = configuration.difficultyFilter();
        eligibleTracks.removeIf(candidate -> !difficultyFilter.matches(candidate.difficulty()));
        if (eligibleTracks.size() < requestedCount) {
            throw new GameConfigurationValidationException(
                    GameConfigurationError.DIFFICULTY_INSUFFICIENT_TRACKS);
        }

        List<GameTrackCandidate> selected = requestedCount == eligibleTracks.size()
                ? new ArrayList<>(eligibleTracks)
                : sampleWithoutReplacement(eligibleTracks, requestedCount);
        shuffle(selected);

        List<GameTrackSnapshot> snapshots = new ArrayList<>(requestedCount);
        for (int playOrder = 0; playOrder < selected.size(); playOrder++) {
            snapshots.add(GameTrackSnapshot.atOrder(selected.get(playOrder), playOrder));
        }
        return new GameTrackSelection(snapshots);
    }

    private List<GameTrackCandidate> distinctEligibleTracks(
            List<GameTrackCandidate> playlistTracks) {
        Map<UUID, GameTrackCandidate> distinct = new LinkedHashMap<>();
        for (GameTrackCandidate candidate : playlistTracks) {
            Objects.requireNonNull(candidate, "playlist track is required");
            if (candidate.eligiblePublished()) {
                distinct.putIfAbsent(candidate.trackId(), candidate);
            }
        }
        return new ArrayList<>(distinct.values());
    }

    private List<GameTrackCandidate> sampleWithoutReplacement(
            List<GameTrackCandidate> eligibleTracks,
            int requestedCount) {
        List<GameTrackCandidate> pool = new ArrayList<>(eligibleTracks);
        for (int index = 0; index < requestedCount; index++) {
            int selectedIndex = index + nextInt(pool.size() - index);
            Collections.swap(pool, index, selectedIndex);
        }
        return new ArrayList<>(pool.subList(0, requestedCount));
    }

    private void shuffle(List<GameTrackCandidate> selected) {
        for (int index = selected.size() - 1; index > 0; index--) {
            Collections.swap(selected, index, nextInt(index + 1));
        }
    }

    private int nextInt(int bound) {
        int value = random.nextInt(bound);
        if (value < 0 || value >= bound) {
            throw new IllegalStateException("game random returned a value outside its bound");
        }
        return value;
    }
}
