package com.blindtest.security;

/** Generates opaque, unpredictable authentication tokens. */
@FunctionalInterface
public interface TokenGenerator {

    /** Returns a new opaque token. */
    String generate();
}
