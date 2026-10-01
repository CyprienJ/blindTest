package com.blindtest.repository;

import java.util.UUID;

/** Minimal track projection used to reconcile stored covers. */
public interface TrackCoverReference {

    /** Returns the owning track ID. */
    UUID getId();

    /** Returns the stored cover ID. */
    UUID getCoverId();
}
