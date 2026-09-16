package com.blindtest.repository;

import java.util.UUID;

/** Minimal track projection used by media reconciliation. */
public interface TrackMediaReference {

    /** Returns the owning track ID. */
    UUID getId();

    /** Returns the internal object key referenced by the track. */
    String getAudioObjectKey();
}
