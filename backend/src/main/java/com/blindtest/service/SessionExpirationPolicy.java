package com.blindtest.service;

import com.blindtest.config.properties.SessionProperties;
import com.blindtest.model.game.GameStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Applies sliding inactive and short terminal retention windows to live snapshots. */
@Component
public class SessionExpirationPolicy {

    private final SessionProperties properties;
    private final Clock clock;

    public SessionExpirationPolicy(SessionProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties are required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /** Returns authoritative UTC application time for session lifecycle decisions. */
    public Instant instant() {
        return clock.instant();
    }

    /** Returns the exclusive expiry instant for one state transition or creation. */
    public Instant expiresAt(GameStatus status, Instant activityAt) {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(activityAt, "activityAt is required");
        return activityAt.plus(switch (status) {
            case CREATED, PLAYING -> properties.inactiveTtl();
            case FINISHED, ABORTED -> properties.finishedTtl();
        });
    }
}
