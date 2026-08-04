package com.blindtest.model.game;

import java.util.List;
import java.util.Objects;

/** Immutable normalized answer text and its consistently normalized tokens. */
public record NormalizedAnswer(String value, List<String> tokens) {

    /** Defensively copies tokens and rejects an inconsistent normalized value. */
    public NormalizedAnswer {
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(tokens, "tokens are required");
        tokens = List.copyOf(tokens);
        if (tokens.stream().anyMatch(token -> token.isBlank()
                || !token.codePoints().allMatch(Character::isLetterOrDigit))) {
            throw new IllegalArgumentException("tokens must contain only letters and digits");
        }
        if (!value.equals(String.join(" ", tokens))) {
            throw new IllegalArgumentException("value must equal the joined tokens");
        }
    }

    /** Returns whether normalization produced no letter or digit token. */
    public boolean isEmpty() {
        return tokens.isEmpty();
    }
}
