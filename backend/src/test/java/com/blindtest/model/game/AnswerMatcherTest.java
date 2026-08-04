package com.blindtest.model.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AnswerMatcherTest {

    private final AnswerMatcher matcher = new AnswerMatcher();

    @ParameterizedTest(name = "accepted: {0}")
    @MethodSource("acceptedCorpus")
    void acceptsReviewedExactAliasTokenAndTypoCorpus(
            String scenario,
            String guess,
            GameAnswerSnapshot answers,
            GameAnswerType expectedType) {
        AnswerMatchResult result = matcher.match(guess, answers);

        assertThat(result.matched()).as(scenario).isTrue();
        assertThat(result.matchedType()).contains(expectedType);
    }

    @ParameterizedTest(name = "rejected: {0}")
    @MethodSource("rejectedCorpus")
    void rejectsReviewedFalsePositiveCorpus(
            String scenario,
            String guess,
            GameAnswerSnapshot answers) {
        AnswerMatchResult result = matcher.match(guess, answers);

        assertThat(result.matched()).as(scenario).isFalse();
        assertThat(result.matchedType()).isEmpty();
        assertThat(result).isEqualTo(AnswerMatchResult.noMatch());
        assertThat(result.toString()).isEqualTo("AnswerMatchResult[matchedType=Optional.empty]");
    }

    @Test
    void exactMatchesAlwaysWinBeforeTolerantTitleMatches() {
        GameAnswerSnapshot answers = new GameAnswerSnapshot(
                "Bohemian Rhapsody",
                "Bohemian Rapsody",
                null,
                null,
                Map.of());

        AnswerMatchResult result = matcher.match("Bohemian Rapsody", answers);

        assertThat(result.matchedType()).contains(GameAnswerType.ARTIST);
    }

    @Test
    void titleWinsDeterministicTieWithinTheSameMatchTier() {
        GameAnswerSnapshot answers = new GameAnswerSnapshot(
                "Phoenix", "Phoenix", null, null, Map.of());

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(matcher.match("PHOENIX", answers).matchedType()).
                    contains(GameAnswerType.TITLE);
        }
    }

    @Test
    void aliasesUseTheirConfiguredAnswerTypeAndTypoTolerance() {
        GameAnswerSnapshot answers = new GameAnswerSnapshot(
                "The Fellowship of the Ring",
                "Howard Shore",
                null,
                null,
                Map.of(
                        GameAnswerType.TITLE, List.of("La Communauté de l'Anneau", "LOTR"),
                        GameAnswerType.ARTIST, List.of("Howie Shore")));

        assertThat(matcher.match("la communaute de l aneau", answers).matchedType()).
                contains(GameAnswerType.TITLE);
        assertThat(matcher.match("lotr", answers).matchedType()).
                contains(GameAnswerType.TITLE);
        assertThat(matcher.match("Howie Shore", answers).matchedType()).
                contains(GameAnswerType.ARTIST);
    }

    @Test
    void normalizationArticlePolicyIsExplicitlyConfigurable() {
        GameAnswerSnapshot answers = new GameAnswerSnapshot(
                "The Beatles", "The Beatles", null, null, Map.of());
        AnswerMatcher preservingMatcher = new AnswerMatcher(
                new AnswerNormalizer(), AnswerNormalizationOptions.preservingArticles());

        assertThat(matcher.match("Beatles", answers).matchedType()).
                contains(GameAnswerType.TITLE);
        assertThat(preservingMatcher.match("Beatles", answers)).
                isEqualTo(AnswerMatchResult.noMatch());
    }

    @Test
    void malformedOrAbuseSizedGuessesFailWithoutAnswerDisclosure() {
        GameAnswerSnapshot answers = standardAnswers();

        assertThat(matcher.match(" !!! 🎵 ", answers)).isEqualTo(AnswerMatchResult.noMatch());
        assertThat(matcher.match("x".repeat(1025), answers)).
                isEqualTo(AnswerMatchResult.noMatch());
        assertThatThrownBy(() -> matcher.match(null, answers)).
                isInstanceOf(NullPointerException.class).
                hasMessage("guess is required");
        assertThatThrownBy(() -> matcher.match("answer", null)).
                isInstanceOf(NullPointerException.class).
                hasMessage("answers are required");
    }

    @Test
    void resultFactoriesEnforceSafeShape() {
        AnswerMatchResult noMatch = AnswerMatchResult.noMatch();

        assertThat(noMatch.matched()).isFalse();
        assertThat(noMatch.matchedType()).isEqualTo(Optional.empty());
        assertThat(AnswerMatchResult.matched(GameAnswerType.ALBUM).matchedType()).
                contains(GameAnswerType.ALBUM);
        assertThatThrownBy(() -> new AnswerMatchResult(null)).
                isInstanceOf(NullPointerException.class).
                hasMessage("matchedType is required");
    }

    @Test
    void matchesTenThousandRepresentativeGuessesWithinRegressionBudget() {
        GameAnswerSnapshot answers = standardAnswers();

        assertTimeout(Duration.ofSeconds(2), () -> {
            for (int iteration = 0; iteration < 10_000; iteration++) {
                AnswerMatchResult result = matcher.match("Bohemain Rhapsody", answers);
                assertThat(result.matchedType()).contains(GameAnswerType.TITLE);
            }
        });
    }

    private static Stream<Arguments> acceptedCorpus() {
        GameAnswerSnapshot answers = standardAnswers();
        return Stream.of(
                Arguments.of("normalized exact title", "  BOHÉMIAN—RHAPSODY! ", answers,
                        GameAnswerType.TITLE),
                Arguments.of("adjacent title transposition", "Bohemain Rhapsody", answers,
                        GameAnswerType.TITLE),
                Arguments.of("exact short artist", "queen", answers, GameAnswerType.ARTIST),
                Arguments.of("composer deletion", "Freddie Mecury", answers,
                        GameAnswerType.COMPOSER),
                Arguments.of("album deletion", "Night at the Opra", answers,
                        GameAnswerType.ALBUM),
                Arguments.of("title alias exact", "Rapsodia Boheme", answers,
                        GameAnswerType.TITLE),
                Arguments.of("artist alias exact", "Queen Band", answers,
                        GameAnswerType.ARTIST),
                Arguments.of("punctuation token boundary", "ACDC", acdcAnswers(),
                        GameAnswerType.TITLE),
                Arguments.of("digits exact", "1984", numericAnswers(), GameAnswerType.TITLE));
    }

    private static Stream<Arguments> rejectedCorpus() {
        GameAnswerSnapshot answers = standardAnswers();
        return Stream.of(
                Arguments.of("short answer substitution", "Quean", answers),
                Arguments.of("partial title", "Bohemian", answers),
                Arguments.of("different long album", "Night at the Office", answers),
                Arguments.of("different artist", "Queens of the Stone Age", answers),
                Arguments.of("blank guess", "  ", answers),
                Arguments.of("short digits substitution", "1985", numericAnswers()),
                Arguments.of("short transposition", "ABAB", abbaAnswers()),
                Arguments.of("missing significant title word", "Dark Side", darkSideAnswers()));
    }

    private static GameAnswerSnapshot standardAnswers() {
        return new GameAnswerSnapshot(
                "Bohemian Rhapsody",
                "Queen",
                "Freddie Mercury",
                "A Night at the Opera",
                Map.of(
                        GameAnswerType.TITLE, List.of("Rapsódia Bohème"),
                        GameAnswerType.ARTIST, List.of("Queen Band")));
    }

    private static GameAnswerSnapshot acdcAnswers() {
        return new GameAnswerSnapshot("AC/DC", "AC/DC", null, null, Map.of());
    }

    private static GameAnswerSnapshot numericAnswers() {
        return new GameAnswerSnapshot("1984", "Van Halen", null, null, Map.of());
    }

    private static GameAnswerSnapshot abbaAnswers() {
        return new GameAnswerSnapshot("ABBA", "ABBA", null, null, Map.of());
    }

    private static GameAnswerSnapshot darkSideAnswers() {
        return new GameAnswerSnapshot(
                "The Dark Side of the Moon", "Pink Floyd", null, null, Map.of());
    }
}
