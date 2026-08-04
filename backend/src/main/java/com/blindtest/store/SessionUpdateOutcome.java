package com.blindtest.store;

/** Outcome of an optimistic compare-and-set session update. */
public enum SessionUpdateOutcome {
    UPDATED,
    VERSION_CONFLICT,
    NOT_FOUND
}
