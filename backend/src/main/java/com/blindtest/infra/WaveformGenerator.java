package com.blindtest.infra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/** Generates a bounded, normalized waveform summary from an audio file through FFmpeg. */
@Component
public class WaveformGenerator {

    static final int TARGET_PEAK_COUNT = 800;
    private static final int SAMPLE_RATE = 100;
    private static final int MAX_PCM_BYTES = 20 * 1024 * 1024;
    private static final long TERMINATION_GRACE_SECONDS = 2;

    private final ProcessFactory processFactory;

    public WaveformGenerator(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    /** Returns up to 800 peak amplitudes normalized to the inclusive range 0..100. */
    public List<Integer> generate(Path audioFile, Duration timeout)
            throws IOException, InterruptedException {
        Process process = processFactory.start(List.of(
                "ffmpeg", "-v", "error", "-threads", "1", "-i", audioFile.toString(),
                "-map", "0:a:0", "-ac", "1", "-ar", Integer.toString(SAMPLE_RATE),
                "-f", "s16le", "pipe:1"));
        try (ExecutorService readerExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> output = readerExecutor.submit(() -> readBounded(process));
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminate(process);
                throw new IOException("Waveform generation timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("FFmpeg could not generate waveform samples");
            }
            try {
                return normalize(output.get(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS));
            } catch (ExecutionException | TimeoutException exception) {
                terminate(process);
                throw new IOException("Could not read waveform samples", exception);
            }
        } finally {
            if (process.isAlive()) {
                terminate(process);
            }
        }
    }

    private byte[] readBounded(Process process) throws IOException {
        try (InputStream input = process.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_PCM_BYTES) {
                    process.destroyForcibly();
                    throw new IOException("Waveform output exceeded its safety limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private List<Integer> normalize(byte[] pcm) throws IOException {
        int sampleCount = pcm.length / 2;
        if (sampleCount == 0) {
            throw new IOException("FFmpeg returned no waveform samples");
        }
        int peakCount = Math.min(TARGET_PEAK_COUNT, sampleCount);
        int[] peaks = new int[peakCount];
        int globalMaximum = 0;
        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            int offset = sampleIndex * 2;
            short sample = (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
            int amplitude = Math.abs((int) sample);
            int bucket = Math.min(peakCount - 1, sampleIndex * peakCount / sampleCount);
            peaks[bucket] = Math.max(peaks[bucket], amplitude);
            globalMaximum = Math.max(globalMaximum, amplitude);
        }
        List<Integer> normalized = new ArrayList<>(peakCount);
        for (int peak : peaks) {
            normalized.add(globalMaximum == 0 ? 0 : Math.round(peak * 100f / globalMaximum));
        }
        return List.copyOf(normalized);
    }

    private void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
    }
}
