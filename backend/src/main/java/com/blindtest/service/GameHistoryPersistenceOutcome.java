package com.blindtest.service;

/** Result of idempotently materializing one completed active session. */
public enum GameHistoryPersistenceOutcome {
    CREATED,
    ALREADY_EXISTS
}
