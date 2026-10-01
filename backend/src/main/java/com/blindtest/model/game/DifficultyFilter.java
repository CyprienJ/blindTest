package com.blindtest.model.game;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validated track difficulty levels included in a game.
 *
 * @param levels included star levels, each between {@link #MINIMUM_LEVEL} and {@link #MAXIMUM_LEVEL}
 * @param includeUnrated whether tracks without an administrator difficulty are included
 */
public record DifficultyFilter(Set<Integer> levels, boolean includeUnrated) {

    public static final int MINIMUM_LEVEL = 1;
    public static final int MAXIMUM_LEVEL = 5;
    /** Every level plus unrated tracks: the filter applied when a player selects nothing. */
    public static final DifficultyFilter ALL = new DifficultyFilter(Set.of(1, 2, 3, 4, 5), true);

    /** Snapshots the levels in ascending order. */
    public DifficultyFilter {
        levels = Collections.unmodifiableSet(new TreeSet<>(levels));
    }

    /** Returns whether a level belongs to the approved star range. */
    public static boolean isAllowedLevel(Integer level) {
        return level != null && level >= MINIMUM_LEVEL && level <= MAXIMUM_LEVEL;
    }

    /** Returns whether a track with the given difficulty, possibly unrated, is included. */
    public boolean matches(Integer difficulty) {
        return difficulty == null ? includeUnrated : levels.contains(difficulty);
    }

    /** Returns whether the filter keeps every track. */
    public boolean includesEverything() {
        return includeUnrated && levels.size() == MAXIMUM_LEVEL - MINIMUM_LEVEL + 1;
    }
}
