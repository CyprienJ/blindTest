package com.blindtest.model.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure deterministic matcher for canonical and administrator-configured game answers. */
public final class AnswerMatcher {

    private static final int MAX_GUESS_CODE_POINTS = 1024;
    private static final int MIN_COMPACT_EQUIVALENCE_LENGTH = 4;

    private final AnswerNormalizer normalizer;
    private final AnswerNormalizationOptions normalizationOptions;
    private final DamerauLevenshtein distance = new DamerauLevenshtein();

    /** Creates the V1 matcher with common leading-article removal enabled. */
    public AnswerMatcher() {
        this(new AnswerNormalizer(),
                AnswerNormalizationOptions.removingCommonLeadingArticle());
    }

    /** Creates a matcher with an explicit normalization policy. */
    public AnswerMatcher(
            AnswerNormalizer normalizer,
            AnswerNormalizationOptions normalizationOptions) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer is required");
        this.normalizationOptions = Objects.requireNonNull(
                normalizationOptions, "normalizationOptions are required");
    }

    /**
     * Matches a guess by exact value, token-boundary equivalence, then bounded typo tolerance.
     * Successful results contain only the answer type; rejected guesses expose no answer data.
     */
    public AnswerMatchResult match(String guess, GameAnswerSnapshot answers) {
        Objects.requireNonNull(guess, "guess is required");
        Objects.requireNonNull(answers, "answers are required");

        NormalizedAnswer normalizedGuess = normalizer.normalize(guess, normalizationOptions);
        if (normalizedGuess.isEmpty()
                || normalizedGuess.value().codePointCount(0, normalizedGuess.value().length())
                        > MAX_GUESS_CODE_POINTS) {
            return AnswerMatchResult.noMatch();
        }
        List<AnswerCandidate> candidates = candidates(answers);

        AnswerMatchResult exact = firstMatch(
                candidates, candidate -> normalizedGuess.equals(candidate.answer()));
        if (exact.matched()) {
            return exact;
        }
        AnswerMatchResult tokenEquivalent = firstMatch(
                candidates, candidate -> tokenEquivalent(normalizedGuess, candidate.answer()));
        if (tokenEquivalent.matched()) {
            return tokenEquivalent;
        }
        return firstMatch(
                candidates, candidate -> toleratesTypo(normalizedGuess, candidate.answer()));
    }

    private List<AnswerCandidate> candidates(GameAnswerSnapshot answers) {
        List<AnswerCandidate> candidates = new ArrayList<>();
        for (GameAnswerType type : GameAnswerType.values()) {
            Set<NormalizedAnswer> distinct = new LinkedHashSet<>();
            addCandidate(distinct, canonicalValue(answers, type));
            answers.aliases().getOrDefault(type, List.of()).
                    forEach(alias -> addCandidate(distinct, alias));
            distinct.forEach(answer -> candidates.add(new AnswerCandidate(type, answer)));
        }
        return candidates;
    }

    private void addCandidate(Set<NormalizedAnswer> candidates, String value) {
        if (value != null) {
            NormalizedAnswer normalized = normalizer.normalize(value, normalizationOptions);
            if (!normalized.isEmpty()) {
                candidates.add(normalized);
            }
        }
    }

    private String canonicalValue(GameAnswerSnapshot answers, GameAnswerType type) {
        return switch (type) {
            case TITLE -> answers.title();
            case ARTIST -> answers.artist();
            case COMPOSER -> answers.composer();
            case ALBUM -> answers.album();
        };
    }

    private AnswerMatchResult firstMatch(
            List<AnswerCandidate> candidates,
            CandidateRule rule) {
        for (AnswerCandidate candidate : candidates) {
            if (rule.matches(candidate)) {
                return AnswerMatchResult.matched(candidate.type());
            }
        }
        return AnswerMatchResult.noMatch();
    }

    private boolean tokenEquivalent(NormalizedAnswer guess, NormalizedAnswer expected) {
        String compactGuess = compact(guess);
        String compactExpected = compact(expected);
        return compactExpected.codePointCount(0, compactExpected.length())
                        >= MIN_COMPACT_EQUIVALENCE_LENGTH
                && compactGuess.equals(compactExpected);
    }

    private String compact(NormalizedAnswer answer) {
        return String.join("", answer.tokens());
    }

    private boolean toleratesTypo(NormalizedAnswer guess, NormalizedAnswer expected) {
        int guessLength = guess.value().codePointCount(0, guess.value().length());
        int expectedLength = expected.value().codePointCount(0, expected.value().length());
        int allowedDistance = allowedDistance(Math.max(guessLength, expectedLength));
        if (allowedDistance == 0 || Math.abs(guessLength - expectedLength) > allowedDistance) {
            return false;
        }
        return distance.distance(guess.value(), expected.value()) <= allowedDistance;
    }

    private int allowedDistance(int length) {
        if (length <= 5) {
            return 0;
        }
        if (length <= 12) {
            return 1;
        }
        if (length <= 20) {
            return 2;
        }
        return 3;
    }

    private record AnswerCandidate(GameAnswerType type, NormalizedAnswer answer) {
    }

    @FunctionalInterface
    private interface CandidateRule {
        boolean matches(AnswerCandidate candidate);
    }
}
