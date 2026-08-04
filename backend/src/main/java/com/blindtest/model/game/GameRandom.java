package com.blindtest.model.game;

/** Injectable game-domain randomness port. */
@FunctionalInterface
public interface GameRandom {

    /** Returns a value between zero (inclusive) and {@code bound} (exclusive). */
    int nextInt(int bound);
}
