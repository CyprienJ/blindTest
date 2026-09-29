package com.blindtest.infra;

import java.time.Duration;

/** Facts read from a supported audio file; tags are {@code null} when absent or blank. */
public record ProbedAudio(Duration duration, String title, String artist) {
}
