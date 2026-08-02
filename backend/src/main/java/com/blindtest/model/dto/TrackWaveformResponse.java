package com.blindtest.model.dto;

import java.util.List;

/** Bounded waveform data used by the administrator excerpt selector. */
public record TrackWaveformResponse(Integer durationSeconds, List<Integer> peaks) {
}
