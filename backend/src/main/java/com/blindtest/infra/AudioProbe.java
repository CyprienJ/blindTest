package com.blindtest.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Identifies uploaded audio through {@code ffprobe} instead of trusting its name or declared type.
 * Only allow-listed containers whose first audio stream uses an allow-listed codec are accepted.
 */
@Component
public class AudioProbe {

    static final Set<String> SUPPORTED_FORMATS = Set.of(
            "mp3", "mov", "mp4", "m4a", "ogg", "flac", "wav", "matroska", "webm", "aac");
    static final Set<String> SUPPORTED_CODECS = Set.of(
            "mp3", "aac", "alac", "flac", "vorbis", "opus");
    private static final String PCM_CODEC_PREFIX = "pcm_";
    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final long TERMINATION_GRACE_SECONDS = 2;

    private final ProcessFactory processFactory;
    private final ObjectMapper objectMapper;

    public AudioProbe(ProcessFactory processFactory, ObjectMapper objectMapper) {
        this.processFactory = processFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the probed audio facts, or empty when the file is unreadable, unsupported, or takes
     * longer than {@code timeout} to inspect. Only failing to start {@code ffprobe} throws.
     */
    public Optional<ProbedAudio> probe(Path file, Duration timeout) throws IOException, InterruptedException {
        Process process = processFactory.start(List.of(
                "ffprobe", "-v", "error", "-hide_banner", "-print_format", "json",
                "-show_entries", "format=format_name,duration:format_tags:stream=codec_type,codec_name:stream_tags",
                "-i", file.toString()));
        try (ExecutorService readerExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> output = readerExecutor.submit(() -> readBounded(process));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                terminate(process);
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            byte[] json;
            try {
                json = output.get(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException exception) {
                return Optional.empty();
            }
            return interpret(json);
        } finally {
            if (process.isAlive()) {
                terminate(process);
            }
        }
    }

    private Optional<ProbedAudio> interpret(byte[] json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException exception) {
            return Optional.empty();
        }
        JsonNode format = root.path("format");
        boolean supportedFormat = Arrays.stream(format.path("format_name").asText("").split(",")).
                anyMatch(SUPPORTED_FORMATS::contains);
        JsonNode audioStream = firstAudioStream(root.path("streams"));
        Optional<Duration> duration = duration(format.path("duration"));
        if (!supportedFormat || audioStream == null || !isSupportedCodec(audioStream) || duration.isEmpty()) {
            return Optional.empty();
        }
        JsonNode formatTags = format.path("tags");
        JsonNode streamTags = audioStream.path("tags");
        return Optional.of(new ProbedAudio(
                duration.get(),
                firstTag("title", formatTags, streamTags),
                firstTag("artist", formatTags, streamTags)));
    }

    private JsonNode firstAudioStream(JsonNode streams) {
        for (JsonNode stream : streams) {
            if ("audio".equals(stream.path("codec_type").asText())) {
                return stream;
            }
        }
        return null;
    }

    private boolean isSupportedCodec(JsonNode stream) {
        String codec = stream.path("codec_name").asText("");
        return SUPPORTED_CODECS.contains(codec) || codec.startsWith(PCM_CODEC_PREFIX);
    }

    private Optional<Duration> duration(JsonNode value) {
        try {
            double seconds = Double.parseDouble(value.asText(""));
            if (!Double.isFinite(seconds) || seconds <= 0) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(Math.round(seconds * 1000)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** Tag keys differ in case between ID3, Vorbis comments, and MP4 atoms. */
    private String firstTag(String key, JsonNode... tagSets) {
        for (JsonNode tags : tagSets) {
            Iterator<Map.Entry<String, JsonNode>> fields = tags.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String value = field.getValue().asText("").strip();
                if (field.getKey().equalsIgnoreCase(key) && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private byte[] readBounded(Process process) throws IOException {
        try (InputStream input = process.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_OUTPUT_BYTES) {
                    process.destroyForcibly();
                    throw new IOException("ffprobe output exceeded its safety limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
    }
}
