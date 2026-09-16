package com.blindtest.model.game;

import java.util.HashMap;
import java.util.Map;

/** Deterministic unrestricted Damerau-Levenshtein distance over Unicode code points. */
final class DamerauLevenshtein {

    int distance(String sourceValue, String targetValue) {
        int[] source = sourceValue.codePoints().toArray();
        int[] target = targetValue.codePoints().toArray();
        int maximumDistance = source.length + target.length;
        int[][] distances = new int[source.length + 2][target.length + 2];
        initialize(distances, source.length, target.length, maximumDistance);

        Map<Integer, Integer> lastRows = new HashMap<>();
        for (int sourceIndex = 1; sourceIndex <= source.length; sourceIndex++) {
            int lastMatchingColumn = 0;
            for (int targetIndex = 1; targetIndex <= target.length; targetIndex++) {
                int transpositionRow = lastRows.getOrDefault(target[targetIndex - 1], 0);
                int transpositionColumn = lastMatchingColumn;
                int substitutionCost = 1;
                if (source[sourceIndex - 1] == target[targetIndex - 1]) {
                    substitutionCost = 0;
                    lastMatchingColumn = targetIndex;
                }
                distances[sourceIndex + 1][targetIndex + 1] = minimum(
                        distances[sourceIndex][targetIndex] + substitutionCost,
                        distances[sourceIndex + 1][targetIndex] + 1,
                        distances[sourceIndex][targetIndex + 1] + 1,
                        distances[transpositionRow][transpositionColumn]
                                + sourceIndex - transpositionRow - 1
                                + 1
                                + targetIndex - transpositionColumn - 1);
            }
            lastRows.put(source[sourceIndex - 1], sourceIndex);
        }
        return distances[source.length + 1][target.length + 1];
    }

    private void initialize(
            int[][] distances,
            int sourceLength,
            int targetLength,
            int maximumDistance) {
        distances[0][0] = maximumDistance;
        for (int sourceIndex = 0; sourceIndex <= sourceLength; sourceIndex++) {
            distances[sourceIndex + 1][0] = maximumDistance;
            distances[sourceIndex + 1][1] = sourceIndex;
        }
        for (int targetIndex = 0; targetIndex <= targetLength; targetIndex++) {
            distances[0][targetIndex + 1] = maximumDistance;
            distances[1][targetIndex + 1] = targetIndex;
        }
    }

    private int minimum(int first, int second, int third, int fourth) {
        return Math.min(Math.min(first, second), Math.min(third, fourth));
    }
}
