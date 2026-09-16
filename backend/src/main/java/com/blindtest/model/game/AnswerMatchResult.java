package com.blindtest.model.game;

import java.util.Objects;
import java.util.Optional;

/** Safe answer-matching result that never carries canonical or alternative answer text. */
public record AnswerMatchResult(Optional<GameAnswerType> matchedType) {

    private static final AnswerMatchResult NO_MATCH = new AnswerMatchResult(Optional.empty());

    /** Requires a non-null optional answer type. */
    public AnswerMatchResult {
        Objects.requireNonNull(matchedType, "matchedType is required");
    }

    /** Returns the shared result for a rejected guess. */
    public static AnswerMatchResult noMatch() {
        return NO_MATCH;
    }

    /** Returns a successful result containing only the matched answer category. */
    public static AnswerMatchResult matched(GameAnswerType type) {
        return new AnswerMatchResult(Optional.of(Objects.requireNonNull(type, "type is required")));
    }

    /** Returns whether an answer category matched. */
    public boolean matched() {
        return matchedType.isPresent();
    }
}
