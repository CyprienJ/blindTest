package com.blindtest.repository;

import java.util.UUID;

/** Minimal playlist projection used to reconcile stored covers. */
public interface PlaylistCoverReference {

    /** Returns the stored cover ID. */
    UUID getCoverId();
}
