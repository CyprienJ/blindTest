package com.blindtest.service;

import java.util.Optional;

/** Guest nickname rules (ADR-0020): 2 to 20 characters after trimming, printable text only. */
final class GuestNickname {

    static final int MINIMUM_LENGTH = 2;
    static final int MAXIMUM_LENGTH = 20;

    private GuestNickname() {
    }

    /** Returns the trimmed nickname, or empty when it breaks a rule. */
    static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length < MINIMUM_LENGTH || length > MAXIMUM_LENGTH
                || trimmed.codePoints().anyMatch(GuestNickname::isInvisible)) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    private static boolean isInvisible(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
