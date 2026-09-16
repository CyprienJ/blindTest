package com.blindtest.model.game;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Locale-independent Unicode normalization for gameplay answers. */
public final class AnswerNormalizer {

    private static final Set<String> COMMON_LEADING_ARTICLES = Set.of(
            "a", "an", "the",
            "l", "le", "la", "les", "un", "une",
            "des", "du");

    /** Normalizes text while preserving every token, including articles. */
    public NormalizedAnswer normalize(String value) {
        return normalize(value, AnswerNormalizationOptions.preservingArticles());
    }

    /**
     * Applies compatibility decomposition, root-locale case folding, accent folding,
     * punctuation separation, whitespace collapse, and the requested article policy.
     */
    public NormalizedAnswer normalize(String value, AnswerNormalizationOptions options) {
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(options, "options are required");

        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD).
                toLowerCase(Locale.ROOT);
        String folded = foldLettersAndDigits(decomposed);
        List<String> tokens = tokenize(folded);
        if (options.removeCommonLeadingArticle() && tokens.size() > 1
                && COMMON_LEADING_ARTICLES.contains(tokens.getFirst())) {
            tokens = new ArrayList<>(tokens.subList(1, tokens.size()));
        }
        return new NormalizedAnswer(String.join(" ", tokens), tokens);
    }

    private String foldLettersAndDigits(String value) {
        StringBuilder folded = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> appendFolded(codePoint, folded));
        return folded.toString();
    }

    private void appendFolded(int codePoint, StringBuilder folded) {
        if (isCombiningMark(codePoint)) {
            return;
        }
        switch (codePoint) {
            case 'ß' -> folded.append("ss");
            case 'æ' -> folded.append("ae");
            case 'œ' -> folded.append("oe");
            case 'ø' -> folded.append('o');
            case 'ł' -> folded.append('l');
            case 'đ', 'ð' -> folded.append('d');
            case 'þ' -> folded.append("th");
            default -> {
                if (Character.isLetterOrDigit(codePoint)) {
                    folded.appendCodePoint(codePoint);
                } else {
                    appendSeparator(folded);
                }
            }
        }
    }

    private boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private void appendSeparator(StringBuilder folded) {
        if (!folded.isEmpty() && folded.charAt(folded.length() - 1) != ' ') {
            folded.append(' ');
        }
    }

    private List<String> tokenize(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return List.of(value.strip().split(" +"));
    }
}
