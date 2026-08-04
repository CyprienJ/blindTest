package com.blindtest.service.port;

import java.util.UUID;

/** Generates opaque identities for active sessions and their participants. */
@FunctionalInterface
public interface SessionIdentityGenerator {

    UUID nextId();
}
