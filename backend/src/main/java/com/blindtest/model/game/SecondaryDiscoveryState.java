package com.blindtest.model.game;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable participant-indexed ledger with no cross-participant projection. */
public final class SecondaryDiscoveryState {

    private static final SecondaryDiscoveryState EMPTY =
            new SecondaryDiscoveryState(Map.of());

    private final Map<GameParticipantId, Set<GameAnswerType>> discoveries;

    private SecondaryDiscoveryState(
            Map<GameParticipantId, Set<GameAnswerType>> discoveries) {
        this.discoveries = immutableDiscoveries(discoveries);
    }

    /** Returns the shared empty discovery ledger. */
    public static SecondaryDiscoveryState empty() {
        return EMPTY;
    }

    /** Returns only the requested participant's immutable private projection. */
    public ParticipantDiscoveryProjection projectFor(GameParticipantId participantId) {
        Objects.requireNonNull(participantId, "participantId is required");
        return new ParticipantDiscoveryProjection(
                discoveries.getOrDefault(participantId, Set.of()));
    }

    boolean contains(GameParticipantId participantId, GameAnswerType type) {
        return discoveries.getOrDefault(participantId, Set.of()).contains(type);
    }

    SecondaryDiscoveryState withDiscovery(
            GameParticipantId participantId,
            GameAnswerType type) {
        requireSecondary(type);
        Map<GameParticipantId, Set<GameAnswerType>> copy = new HashMap<>(discoveries);
        EnumSet<GameAnswerType> participantDiscoveries = copy.containsKey(participantId)
                ? EnumSet.copyOf(copy.get(participantId))
                : EnumSet.noneOf(GameAnswerType.class);
        participantDiscoveries.add(type);
        copy.put(participantId, participantDiscoveries);
        return new SecondaryDiscoveryState(copy);
    }

    private static Map<GameParticipantId, Set<GameAnswerType>> immutableDiscoveries(
            Map<GameParticipantId, Set<GameAnswerType>> source) {
        Map<GameParticipantId, Set<GameAnswerType>> copy = new HashMap<>();
        source.forEach((participantId, types) -> {
            Objects.requireNonNull(participantId, "participantId is required");
            Objects.requireNonNull(types, "discovered types are required");
            types.forEach(SecondaryDiscoveryState::requireSecondary);
            copy.put(participantId, Collections.unmodifiableSet(EnumSet.copyOf(types)));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void requireSecondary(GameAnswerType type) {
        if (type == null || type == GameAnswerType.TITLE) {
            throw new IllegalArgumentException("only secondary answer types can be discovered");
        }
    }
}
