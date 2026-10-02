package com.blindtest.model.game;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared participant identities for pure game-domain tests. */
public final class GameTestParticipants {

    public static final GameParticipantId SOLO =
            new GameParticipantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    public static final Set<GameParticipantId> SOLO_ACTIVE = Set.of(SOLO);
    public static final List<GameParticipantId> SOLO_LIST = List.of(SOLO);

    private GameTestParticipants() {
    }

    /** Returns stable distinct participants in join order. */
    public static List<GameParticipantId> players(int count) {
        return java.util.stream.IntStream.range(0, count).
                mapToObj(index -> new GameParticipantId(new UUID(0xB0, index))).
                toList();
    }
}
