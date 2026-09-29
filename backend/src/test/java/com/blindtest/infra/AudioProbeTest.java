package com.blindtest.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AudioProbeTest {

    private static final Path FILE = Path.of("/imports/uploads/incoming.upload");

    @Test
    void readsDurationAndCaseInsensitiveFormatTags() throws Exception {
        List<String> capturedCommand = new ArrayList<>();
        AudioProbe probe = probeReturning(capturedCommand, 0, """
                {
                  "streams": [
                    {"codec_type": "video", "codec_name": "mjpeg"},
                    {"codec_type": "audio", "codec_name": "flac", "tags": {"TITLE": "Stream title"}}
                  ],
                  "format": {
                    "format_name": "flac",
                    "duration": "215.480000",
                    "tags": {"TITLE": "Format title", "ARTIST": " Format artist "}
                  }
                }
                """);

        Optional<ProbedAudio> result = probe.probe(FILE, Duration.ofSeconds(1));

        assertThat(result).contains(new ProbedAudio(Duration.ofMillis(215_480), "Format title", "Format artist"));
        assertThat(capturedCommand).startsWith("ffprobe", "-v", "error").endsWith("-i", FILE.toString());
    }

    @Test
    void fallsBackToAudioStreamTagsAndAcceptsPcmAndMp4Families() throws Exception {
        AudioProbe probe = probeReturning(new ArrayList<>(), 0, """
                {
                  "streams": [{"codec_type": "audio", "codec_name": "pcm_s16le", "tags": {"artist": "Stream artist"}}],
                  "format": {"format_name": "mov,mp4,m4a,3gp,3g2,mj2", "duration": "12.5"}
                }
                """);

        assertThat(probe.probe(FILE, Duration.ofSeconds(1))).
                contains(new ProbedAudio(Duration.ofMillis(12_500), null, "Stream artist"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // video-only container
        """
        {"streams": [{"codec_type": "video", "codec_name": "h264"}],
         "format": {"format_name": "mov,mp4,m4a,3gp,3g2,mj2", "duration": "10"}}
        """,
        // unsupported container
        """
        {"streams": [{"codec_type": "audio", "codec_name": "mp3"}],
         "format": {"format_name": "avi", "duration": "10"}}
        """,
        // unsupported codec
        """
        {"streams": [{"codec_type": "audio", "codec_name": "wmav2"}],
         "format": {"format_name": "matroska,webm", "duration": "10"}}
        """,
        // missing duration
        """
        {"streams": [{"codec_type": "audio", "codec_name": "mp3"}],
         "format": {"format_name": "mp3", "duration": "N/A"}}
        """,
        "not json"
    })
    void rejectsUnsupportedOrIncompleteProbeResults(String output) throws Exception {
        AudioProbe probe = probeReturning(new ArrayList<>(), 0, output);

        assertThat(probe.probe(FILE, Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void rejectsFilesFfprobeCannotRead() throws Exception {
        AudioProbe probe = probeReturning(new ArrayList<>(), 1, "Invalid data found when processing input");

        assertThat(probe.probe(FILE, Duration.ofSeconds(1))).isEmpty();
    }

    private AudioProbe probeReturning(List<String> capturedCommand, int exitCode, String output) {
        return new AudioProbe(command -> {
            capturedCommand.addAll(command);
            return new CompletedProcess(output.getBytes(StandardCharsets.UTF_8), exitCode);
        }, new ObjectMapper());
    }

    private static final class CompletedProcess extends Process {

        private final InputStream output;
        private final int exitCode;

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
