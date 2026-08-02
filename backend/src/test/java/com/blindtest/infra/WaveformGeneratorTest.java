package com.blindtest.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaveformGeneratorTest {

    @Test
    void generatesBoundedNormalizedPeaksThroughFfmpeg() throws Exception {
        List<String> capturedCommand = new ArrayList<>();
        byte[] pcm = pcm((short) 0, (short) 1_000, (short) -2_000, (short) 4_000);
        WaveformGenerator generator = new WaveformGenerator(command -> {
            capturedCommand.addAll(command);
            return new CompletedProcess(pcm);
        });

        List<Integer> peaks = generator.generate(Path.of("/tmp/audio.mp3"), Duration.ofSeconds(1));

        assertThat(peaks).containsExactly(0, 25, 50, 100);
        assertThat(capturedCommand).containsExactly(
                "ffmpeg", "-v", "error", "-threads", "1", "-i", "/tmp/audio.mp3",
                "-map", "0:a:0", "-ac", "1", "-ar", "100", "-f", "s16le", "pipe:1");
    }

    private byte[] pcm(short... samples) {
        byte[] bytes = new byte[samples.length * 2];
        for (int index = 0; index < samples.length; index++) {
            bytes[index * 2] = (byte) samples[index];
            bytes[index * 2 + 1] = (byte) (samples[index] >>> 8);
        }
        return bytes;
    }

    private static final class CompletedProcess extends Process {

        private final InputStream output;

        private CompletedProcess(byte[] output) {
            this.output = new ByteArrayInputStream(output);
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return output;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            // The deterministic process has already completed.
        }
    }
}
