package com.blindtest.model.game;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable canonical and alternative answers detached from the catalogue. */
public record GameAnswerSnapshot(
        String title,
        String artist,
        String composer,
        String album,
        Map<GameAnswerType, List<String>> aliases) {

    /** Validates canonical answers and deeply copies every alternative-answer collection. */
    public GameAnswerSnapshot {
        requireText(title, "title");
        requireText(artist, "artist");
        requireOptionalText(composer, "composer");
        requireOptionalText(album, "album");
        aliases = immutableAliases(aliases);
    }

    private static Map<GameAnswerType, List<String>> immutableAliases(
            Map<GameAnswerType, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        EnumMap<GameAnswerType, List<String>> copy = new EnumMap<>(GameAnswerType.class);
        source.forEach((type, values) -> {
            if (type == null || values == null) {
                throw new IllegalArgumentException("alias type and values are required");
            }
            values.forEach(value -> requireText(value, "alias"));
            copy.put(type, List.copyOf(values));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void requireOptionalText(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
    }
}
