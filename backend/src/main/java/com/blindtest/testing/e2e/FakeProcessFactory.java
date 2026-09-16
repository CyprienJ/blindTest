package com.blindtest.testing.e2e;

import com.blindtest.infra.ProcessFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * Fake process adapter that writes a deterministic MP3 and reports yt-dlp-style progress instead
 * of starting the real {@code yt-dlp}/{@code ffmpeg} binaries. Active only under the {@code e2e}
 * Spring profile, and never eligible to run in production; see {@link E2eAdaptersConfig}. Keeps
 * the isolated full-stack environment free of YouTube network access.
 */
public class FakeProcessFactory implements ProcessFactory {

    private static final String FIXTURE_RESOURCE = "/fixtures/e2e-silence.mp3.base64";

    /**
     * A YouTube video ID that deterministically fails the import instead of succeeding, so
     * full-stack specs can exercise the import-process-failure path (E2E-06) the same way a
     * real provider error would surface, without depending on network flakiness to trigger one.
     */
    public static final String FORCED_FAILURE_VIDEO_ID = "e2e-fail-01";

    @Override
    public Process start(List<String> command) throws IOException {
        if (!command.isEmpty() && "ffmpeg".equals(command.getFirst())) {
            byte[] pcm = new byte[1_600];
            for (int index = 0; index < pcm.length / 2; index++) {
                short sample = (short) ((index % 100) * 300);
                pcm[index * 2] = (byte) sample;
                pcm[index * 2 + 1] = (byte) (sample >>> 8);
            }
            return new CompletedProcess(pcm);
        }
        if (command.contains("https://www.youtube.com/watch?v=" + FORCED_FAILURE_VIDEO_ID)) {
            return new CompletedProcess(
                    "ERROR: [youtube] " + FORCED_FAILURE_VIDEO_ID
                            + ": Video unavailable (deterministic e2e failure fixture)\n",
                    1);
        }
        int outputFlagIndex = command.indexOf("--output");
        if (outputFlagIndex < 0 || outputFlagIndex + 1 >= command.size()) {
            throw new IOException("Missing yt-dlp output argument");
        }

        Path outputTemplate = Path.of(command.get(outputFlagIndex + 1));
        Files.createDirectories(outputTemplate.getParent());
        Files.write(Path.of(outputTemplate + ".mp3"), fixtureBytes());
        Files.writeString(Path.of(outputTemplate + ".info.json"), """
                {
                  "title": "Deterministic e2e track title",
                  "uploader": "Deterministic e2e channel",
                  "duration": 120,
                  "thumbnail": "https://images.example.test/deterministic-cover.jpg"
                }
                """);
        return new CompletedProcess("[download] 50.0%\n[download] 100.0%\n");
    }

    private static byte[] fixtureBytes() throws IOException {
        try (InputStream input = FakeProcessFactory.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing MP3 fixture " + FIXTURE_RESOURCE);
            }
            String encoded = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            return Base64.getMimeDecoder().decode(encoded);
        }
    }

    private static final class CompletedProcess extends Process {

        private final InputStream output;
        private final int exitCode;

        private CompletedProcess(String output) {
            this(output, 0);
        }

        private CompletedProcess(String output, int exitCode) {
            this(output.getBytes(StandardCharsets.UTF_8), exitCode);
        }

        private CompletedProcess(byte[] output) {
            this(output, 0);
        }

        private CompletedProcess(byte[] output, int exitCode) {
            this.output = new ByteArrayInputStream(output);
            this.exitCode = exitCode;
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
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            // The deterministic process has already completed.
        }
    }
}
