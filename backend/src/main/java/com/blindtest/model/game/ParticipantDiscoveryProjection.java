package com.blindtest.model.game;

import java.util.Objects;
import java.util.Set;

/** Immutable participant-private view of discovered secondary answer categories. */
public record ParticipantDiscoveryProjection(Set<GameAnswerType> discoveredTypes) {

    /** Copies and validates the secondary-only projection. */
    public ParticipantDiscoveryProjection {
        Objects.requireNonNull(discoveredTypes, "discoveredTypes are required");
        discoveredTypes = Set.copyOf(discoveredTypes);
        if (discoveredTypes.contains(GameAnswerType.TITLE)) {
            throw new IllegalArgumentException("only secondary answer types can be discovered");
        }
    }

    /** Returns whether this participant has privately discovered the category. */
    public boolean hasDiscovered(GameAnswerType type) {
        return discoveredTypes.contains(type);
    }
}
