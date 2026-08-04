package com.blindtest.store;

/** Outcome of version-guarded active-session removal. */
public enum SessionRemoveOutcome {
    REMOVED,
    VERSION_CONFLICT,
    NOT_FOUND
}
