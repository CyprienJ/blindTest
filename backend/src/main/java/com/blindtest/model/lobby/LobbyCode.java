package com.blindtest.model.lobby;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Six-character lobby join code from an unambiguous alphabet (no 0, O, 1, or I). */
public record LobbyCode(String value) {

    /** Characters a generated code may contain. */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    private static final Pattern FORMAT = Pattern.compile("[" + ALPHABET + "]{" + LENGTH + "}");

    /** Rejects codes outside the alphabet or length. */
    public LobbyCode {
        Objects.requireNonNull(value, "value is required");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("lobby code is malformed");
        }
    }

    /** Parses user input case-insensitively, returning empty for any malformed code. */
    public static Optional<LobbyCode> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT);
        return FORMAT.matcher(normalized).matches()
                ? Optional.of(new LobbyCode(normalized))
                : Optional.empty();
    }
}
