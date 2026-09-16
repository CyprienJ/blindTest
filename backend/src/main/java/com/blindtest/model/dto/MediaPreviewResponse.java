package com.blindtest.model.dto;

import java.net.URI;
import java.time.Instant;

/** Short-lived authorized media URL returned to an administrator. */
public record MediaPreviewResponse(URI url, Instant expiresAt) {
}
