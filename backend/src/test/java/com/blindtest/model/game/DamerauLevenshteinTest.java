package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DamerauLevenshteinTest {

    private final DamerauLevenshtein distance = new DamerauLevenshtein();

    @Test
    void countsInsertionDeletionSubstitutionAndAdjacentTransposition() {
        assertThat(distance.distance("opera", "opra")).isEqualTo(1);
        assertThat(distance.distance("opra", "opera")).isEqualTo(1);
        assertThat(distance.distance("opera", "opira")).isEqualTo(1);
        assertThat(distance.distance("bohemian", "bohemain")).isEqualTo(1);
    }

    @Test
    void implementsUnrestrictedRatherThanOptimalStringAlignmentDistance() {
        assertThat(distance.distance("CA", "ABC")).isEqualTo(2);
    }

    @Test
    void comparesUnicodeCodePointsRatherThanUtf16CodeUnits() {
        assertThat(distance.distance("a🎵b", "a🎶b")).isEqualTo(1);
    }

    @Test
    void handlesEmptyAndEqualValues() {
        assertThat(distance.distance("", "answer")).isEqualTo(6);
        assertThat(distance.distance("answer", "")).isEqualTo(6);
        assertThat(distance.distance("answer", "answer")).isZero();
    }
}
