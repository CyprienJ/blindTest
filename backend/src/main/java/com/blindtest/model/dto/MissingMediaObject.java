package com.blindtest.model.dto;

import java.util.UUID;

/** Safely identifies a track whose referenced media object is absent. */
public record MissingMediaObject(UUID trackId, String objectFingerprint) {
}
