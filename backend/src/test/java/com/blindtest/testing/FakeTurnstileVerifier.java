package com.blindtest.testing;

import com.blindtest.service.port.TurnstileVerifier;
import java.util.HashSet;
import java.util.Set;

/** Configurable in-memory Turnstile verifier for integration tests. */
public class FakeTurnstileVerifier implements TurnstileVerifier {

    private final Set<String> acceptedTokens = new HashSet<>();

    @Override
    public synchronized boolean verify(String token) {
        return acceptedTokens.contains(token);
    }

    /** Marks a token as accepted. */
    public synchronized void accept(String token) {
        acceptedTokens.add(token);
    }

    /** Clears accepted tokens between tests. */
    public synchronized void reset() {
        acceptedTokens.clear();
    }
}
