package com.blindtest.model.game;

import java.util.Objects;
import java.util.Optional;

/** Safe private feedback and resulting immutable participant discovery state. */
public record SecondaryDiscoveryResult(
        SecondaryDiscoveryOutcome outcome,
        Optional<GameAnswerType> discoveredType,
        SecondaryDiscoveryState state) {

    /** Ensures only a newly discovered secondary category is disclosed. */
    public SecondaryDiscoveryResult {
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(discoveredType, "discoveredType is required");
        Objects.requireNonNull(state, "state is required");
        if (outcome == SecondaryDiscoveryOutcome.DISCOVERED) {
            GameAnswerType type = discoveredType.orElseThrow(
                    () -> new IllegalArgumentException("discovered type is required"));
            if (type == GameAnswerType.TITLE) {
                throw new IllegalArgumentException("discovered type must be secondary");
            }
        } else if (discoveredType.isPresent()) {
            throw new IllegalArgumentException("only a new discovery can disclose its type");
        }
    }
}
