package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AnswerNormalizerTest {

    private final AnswerNormalizer normalizer = new AnswerNormalizer();

    @ParameterizedTest(name = "{0}")
    @MethodSource("reviewedCorpus")
    void normalizesReviewedFrenchAndGeneralAnswerCorpus(
            String scenario,
            String input,
            AnswerNormalizationOptions options,
            String expectedValue,
            List<String> expectedTokens) {
        NormalizedAnswer normalized = normalizer.normalize(input, options);

        assertThat(normalized.value()).isEqualTo(expectedValue);
        assertThat(normalized.tokens()).containsExactlyElementsOf(expectedTokens);
    }

    @Test
    void defaultNormalizationPreservesArticles() {
        assertThat(normalizer.normalize("L'été").value()).isEqualTo("l ete");
        assertThat(normalizer.normalize("The Wall").value()).isEqualTo("the wall");
    }

    @Test
    void articleRemovalNeverEmptiesAShortAnswer() {
        AnswerNormalizationOptions removeArticle =
                AnswerNormalizationOptions.removingCommonLeadingArticle();

        assertThat(normalizer.normalize("A", removeArticle).value()).isEqualTo("a");
        assertThat(normalizer.normalize("L'", removeArticle).value()).isEqualTo("l");
        assertThat(normalizer.normalize("The", removeArticle).value()).isEqualTo("the");
    }

    @Test
    void outputDoesNotDependOnTheJvmDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            NormalizedAnswer turkishDefault = normalizer.normalize("I İ ISTANBUL");
            Locale.setDefault(Locale.FRANCE);
            NormalizedAnswer frenchDefault = normalizer.normalize("I İ ISTANBUL");

            assertThat(turkishDefault).isEqualTo(frenchDefault);
            assertThat(turkishDefault.value()).isEqualTo("i i istanbul");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void blankOrPunctuationOnlyInputProducesAnEmptyNormalizedAnswer() {
        NormalizedAnswer blank = normalizer.normalize(" \t— 🎵 !!! ");

        assertThat(blank.value()).isEmpty();
        assertThat(blank.tokens()).isEmpty();
        assertThat(blank.isEmpty()).isTrue();
    }

    @Test
    void rejectsNullInputAndOptions() {
        assertThatThrownBy(() -> normalizer.normalize(null)).
                isInstanceOf(NullPointerException.class).
                hasMessage("value is required");
        assertThatThrownBy(() -> normalizer.normalize(
                        "answer", (AnswerNormalizationOptions) null)).
                isInstanceOf(NullPointerException.class).
                hasMessage("options are required");
    }

    @Test
    void normalizedAnswerDefensivelyCopiesAndValidatesTokens() {
        List<String> mutableTokens = new java.util.ArrayList<>(List.of("one", "two"));
        NormalizedAnswer answer = new NormalizedAnswer("one two", mutableTokens);
        mutableTokens.set(0, "changed");

        assertThat(answer.tokens()).containsExactly("one", "two");
        assertThatThrownBy(() -> answer.tokens().add("three")).
                isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new NormalizedAnswer("different", List.of("token"))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("value must equal the joined tokens");
        assertThatThrownBy(() -> new NormalizedAnswer("not-normalized", List.of("not-normalized"))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("tokens must contain only letters and digits");
    }

    private static Stream<Arguments> reviewedCorpus() {
        AnswerNormalizationOptions keep = AnswerNormalizationOptions.preservingArticles();
        AnswerNormalizationOptions remove =
                AnswerNormalizationOptions.removingCommonLeadingArticle();
        return Stream.of(
                Arguments.of("precomposed French accents", "Été à Paris", keep,
                        "ete a paris", List.of("ete", "a", "paris")),
                Arguments.of("decomposed accents", "E\u0301le\u0300ve", keep,
                        "eleve", List.of("eleve")),
                Arguments.of("punctuation and whitespace", "  Jean-Jacques\tGoldman / Céline  ",
                        keep, "jean jacques goldman celine",
                        List.of("jean", "jacques", "goldman", "celine")),
                Arguments.of("curly apostrophe and French article", "L’été indien", remove,
                        "ete indien", List.of("ete", "indien")),
                Arguments.of("apostrophe separates a non-article prefix", "D’Artagnan", remove,
                        "d artagnan", List.of("d", "artagnan")),
                Arguments.of("English article", "The Dark Side of the Moon", remove,
                        "dark side of the moon", List.of("dark", "side", "of", "the", "moon")),
                Arguments.of("only one leading article removed", "La La Land", remove,
                        "la land", List.of("la", "land")),
                Arguments.of("digits retained", "007 : James Bond 2", keep,
                        "007 james bond 2", List.of("007", "james", "bond", "2")),
                Arguments.of("compatibility width and French ligature", "Ｆœtus", keep,
                        "foetus", List.of("foetus")),
                Arguments.of("short accented answer", "É", keep, "e", List.of("e")),
                Arguments.of("symbols separate tokens", "AC/DC & R.E.M.", keep,
                        "ac dc r e m", List.of("ac", "dc", "r", "e", "m")),
                Arguments.of("non-Latin letters remain", "東京 ２０２６", keep,
                        "東京 2026", List.of("東京", "2026")));
    }
}
