package com.blindtest.testing;

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

/** Fake process adapter that writes a deterministic MP3 and reports yt-dlp-style progress. */
public class FakeProcessFactory implements ProcessFactory {

    private static final String FIXTURE_RESOURCE = "/fixtures/silence.mp3.base64";
    private int startCount;

    @Override
    public synchronized Process start(List<String> command) throws IOException {
        int outputFlagIndex = command.indexOf("--output");
        if (outputFlagIndex < 0 || outputFlagIndex + 1 >= command.size()) {
            throw new IOException("Missing yt-dlp output argument");
        }

        Path outputTemplate = Path.of(command.get(outputFlagIndex + 1));
        Files.createDirectories(outputTemplate.getParent());
        Files.write(Path.of(outputTemplate + ".mp3"), fixtureBytes());
        startCount++;
        return new CompletedProcess("[download] 50.0%\n[download] 100.0%\n");
    }

    /** Returns how many fake processes were started. */
    public synchronized int startCount() {
        return startCount;
    }

    /** Resets invocation state between tests. */
    public synchronized void reset() {
        startCount = 0;
    }

    private byte[] fixtureBytes() throws IOException {
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

        private CompletedProcess(String output) {
            this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
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
