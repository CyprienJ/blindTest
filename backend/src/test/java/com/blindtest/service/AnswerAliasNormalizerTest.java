package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerAliasNormalizerTest {

    private final AnswerAliasNormalizer normalizer = new AnswerAliasNormalizer();

    @Test
    void normalizesUnicodeCaseAndWhitespaceForDuplicateDetection() {
        assertThat(normalizer.normalize("  ＭY\t  Alias  ")).isEqualTo("my alias");
    }

    @Test
    void doesNotDefineFutureAccentOrPunctuationMatchingRules() {
        assertThat(normalizer.normalize("L'été!")).isEqualTo("l'été!");
    }
}
