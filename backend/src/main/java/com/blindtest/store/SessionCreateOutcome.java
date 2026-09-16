package com.blindtest.store;

/** Outcome of atomically creating an active session. */
public enum SessionCreateOutcome {
    CREATED,
    ALREADY_EXISTS
}
