package com.blindtest.model.game;

/** Domain exception carrying a stable configuration-validation error. */
public final class GameConfigurationValidationException extends RuntimeException {

    private final GameConfigurationError error;

    public GameConfigurationValidationException(GameConfigurationError error) {
        super(error.safeMessage());
        this.error = error;
    }

    public GameConfigurationError error() {
        return error;
    }

    public String code() {
        return error.code();
    }
}
