package com.blindtest.service;

import com.blindtest.config.properties.ImportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Owns the on-disk location of uploaded source files between the upload request and the import
 * worker. Staged files live under the import workspace so restart recovery can resume them.
 * Paths are derived only from server-generated identifiers, never from client file names.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportUploadStorage {

    private static final String UPLOADS_DIRECTORY = "uploads";
    private static final String EXTENSION = ".upload";

    private final ImportProperties importProperties;

    /** Returns a fresh path for receiving a request body before any job exists. */
    public Path newIncomingPath() throws IOException {
        Path directory = uploadsDirectory();
        Files.createDirectories(directory);
        return directory.resolve("incoming-" + UUID.randomUUID() + EXTENSION);
    }

    /** Moves a received file to the location the worker reads for {@code jobId}. */
    public void promote(Path incoming, UUID jobId) throws IOException {
        Files.move(incoming, stagedSource(jobId), StandardCopyOption.ATOMIC_MOVE);
    }

    /** Returns the staged source path for {@code jobId}; it may no longer exist. */
    public Path stagedSource(UUID jobId) {
        return uploadsDirectory().resolve(jobId + EXTENSION);
    }

    /** Deletes the staged source for {@code jobId}, logging instead of failing. */
    public void deleteStagedSource(UUID jobId) {
        deleteQuietly(stagedSource(jobId));
    }

    /** Deletes {@code path} if present, logging instead of failing. */
    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not delete staged upload {}", path.getFileName());
        }
    }

    private Path uploadsDirectory() {
        return importProperties.tempDir().toAbsolutePath().normalize().resolve(UPLOADS_DIRECTORY);
    }
}
