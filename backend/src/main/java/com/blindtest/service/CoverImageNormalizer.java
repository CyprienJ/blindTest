package com.blindtest.service;

import com.blindtest.config.properties.ImportProperties;
import com.blindtest.infra.cover.CoverHttpClient;
import com.blindtest.infra.cover.CoverImageFormat;
import com.blindtest.infra.cover.CoverImageTranscoder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Validates a source image and converts it into the stored cover JPEG. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoverImageNormalizer {

    private final CoverImageTranscoder transcoder;
    private final ImportProperties importProperties;

    /**
     * Returns a metadata-free JPEG of at most 600x600 for a JPEG, PNG, or WebP source.
     *
     * @throws ResponseStatusException {@code COVER_IMAGE_TOO_LARGE} or
     *     {@code COVER_IMAGE_UNSUPPORTED} when the source cannot be used
     */
    public byte[] normalize(byte[] source) {
        if (source.length > CoverHttpClient.MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "COVER_IMAGE_TOO_LARGE");
        }
        CoverImageFormat format = CoverImageFormat.detect(source).orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COVER_IMAGE_UNSUPPORTED"));
        Path input = null;
        Path output = null;
        try {
            Path directory = Files.createDirectories(importProperties.tempDir().resolve("covers"));
            input = Files.createTempFile(directory, "cover-", ".source");
            output = Files.createTempFile(directory, "cover-", ".jpg");
            Files.write(input, source);
            if (!transcode(input, format, output)) {
                throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COVER_IMAGE_UNSUPPORTED");
            }
            return Files.readAllBytes(output);
        } catch (IOException exception) {
            throw new IllegalStateException("Cover workspace is unavailable", exception);
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    private boolean transcode(Path input, CoverImageFormat format, Path output) throws IOException {
        try {
            transcoder.toJpeg(input, format, output);
        } catch (IOException exception) {
            log.info("Cover image rejected: {}", exception.getMessage());
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cover conversion was interrupted", exception);
        }
        return CoverImageFormat.detect(Files.readAllBytes(output)).orElse(null)
                == CoverImageFormat.JPEG;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Could not delete temporary cover file {}", path.getFileName());
        }
    }
}
