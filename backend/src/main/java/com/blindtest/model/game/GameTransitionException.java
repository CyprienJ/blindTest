package com.blindtest.model.game;

/** Domain exception carrying a stable illegal-transition failure. */
public final class GameTransitionException extends RuntimeException {

    private final GameTransitionError error;

    public GameTransitionException(GameTransitionError error) {
        super(error.safeMessage());
        this.error = error;
    }

    public GameTransitionError error() {
        return error;
    }

    public String code() {
        return error.code();
    }
}
