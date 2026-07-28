package com.blindtest.service.port;

/** Port for validating registration anti-bot tokens. */
@FunctionalInterface
public interface TurnstileVerifier {

    /** Returns whether the supplied token is accepted. */
    boolean verify(String token);
}
