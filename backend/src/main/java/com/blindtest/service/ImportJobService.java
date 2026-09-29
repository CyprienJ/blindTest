package com.blindtest.service;

import com.blindtest.model.dto.ImportJobResponse;
import com.blindtest.model.dto.ImportStartResponse;
import com.blindtest.model.dto.ImportTrackRequest;
import com.blindtest.model.dto.UploadTrackRequest;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.ImportSource;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.config.properties.ImportProperties;
import com.blindtest.infra.AudioProbe;
import com.blindtest.infra.ProbedAudio;
import com.blindtest.model.validation.YouTubeVideoUrl;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.port.ImportJobScheduler;
import com.blindtest.service.port.MediaStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Service managing track import job lifecycle. */
@Service
@RequiredArgsConstructor
public class ImportJobService {

    private static final String PENDING_METADATA = "Pending YouTube metadata";
    /** Bounds transcoding work and keeps a 160 kbit/s MP3 well under the default size limit. */
    static final Duration MAX_UPLOAD_DURATION = Duration.ofMinutes(15);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_METADATA_LENGTH = 255;
    private static final Pattern UNSAFE_FILENAME_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]");
    private static final EnumSet<ImportJobStatus> ACTIVE_STATUSES = EnumSet.of(
            ImportJobStatus.QUEUED, ImportJobStatus.DOWNLOADING, ImportJobStatus.PROCESSING);

    private final TrackImportJobRepository jobRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final ImportJobScheduler importJobScheduler;
    private final MediaStorage mediaStorage;
    private final ImportUploadStorage uploadStorage;
    private final AudioProbe audioProbe;
    private final ImportProperties importProperties;
    private final TransactionTemplate transactionTemplate;

    /** Creates a PENDING track and a QUEUED import job, then triggers async download. */
    @Transactional
    public ImportStartResponse startImport(ImportTrackRequest request, UUID adminId) {
        UserEntity admin = userRepository.findById(adminId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        YouTubeVideoUrl youtubeUrl = YouTubeVideoUrl.parse(request.getYoutubeUrl()).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        if (jobRepository.existsByYoutubeVideoIdAndStatusIn(
                youtubeUrl.videoId(), ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IMPORT_ALREADY_ACTIVE");
        }

        TrackEntity track = buildTrack(request, youtubeUrl.canonicalUrl());
        track = trackRepository.save(track);

        TrackImportJobEntity job = new TrackImportJobEntity();
        job.setYoutubeUrl(youtubeUrl.canonicalUrl());
        job.setYoutubeVideoId(youtubeUrl.videoId());
        job.setStatus(ImportJobStatus.QUEUED);
        job.setAdmin(admin);
        job.setTrack(track);
        job = jobRepository.save(job);

        importJobScheduler.schedule(job.getId());
        return new ImportStartResponse(job.getId());
    }

    /**
     * Validates an uploaded audio file with ffprobe, then creates a PENDING track and a QUEUED
     * upload job whose worker re-encodes the staged file. File work happens outside the database
     * transaction; nothing is persisted when the file is rejected.
     */
    public ImportStartResponse startUpload(MultipartFile file, UploadTrackRequest request, UUID adminId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMPORT_FILE_UNSUPPORTED");
        }
        if (file.getSize() > importProperties.maxSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "IMPORT_FILE_TOO_LARGE");
        }
        Path incoming = null;
        try {
            incoming = uploadStorage.newIncomingPath();
            file.transferTo(incoming.toFile());
            ProbedAudio probed = probe(incoming);
            String title = firstNonBlank(request.getTitle(), probed.title());
            String artist = firstNonBlank(request.getArtist(), probed.artist());
            if (title == null || artist == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMPORT_METADATA_REQUIRED");
            }
            Path received = incoming;
            ImportStartResponse response = transactionTemplate.execute(status -> persistUpload(
                    received, adminId, title, artist, probed.duration(), file.getOriginalFilename()));
            incoming = null;
            return response;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not stage the uploaded file", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while inspecting the uploaded file", exception);
        } finally {
            if (incoming != null) {
                uploadStorage.deleteQuietly(incoming);
            }
        }
    }

    private ProbedAudio probe(Path incoming) throws IOException, InterruptedException {
        ProbedAudio probed = audioProbe.probe(incoming, PROBE_TIMEOUT).
                orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMPORT_FILE_UNSUPPORTED"));
        if (probed.duration().compareTo(MAX_UPLOAD_DURATION) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IMPORT_AUDIO_TOO_LONG");
        }
        return probed;
    }

    private ImportStartResponse persistUpload(
            Path incoming, UUID adminId, String title, String artist, Duration duration, String filename) {
        UserEntity admin = userRepository.findById(adminId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        TrackEntity track = new TrackEntity();
        track.setTitle(limit(title));
        track.setArtist(limit(artist));
        track.setDurationSeconds((int) duration.toSeconds());
        track.setStatus(TrackStatus.PENDING);
        track = trackRepository.save(track);

        TrackImportJobEntity job = new TrackImportJobEntity();
        job.setSource(ImportSource.UPLOAD);
        job.setOriginalFilename(displayFilename(filename));
        job.setStatus(ImportJobStatus.QUEUED);
        job.setAdmin(admin);
        job.setTrack(track);
        job = jobRepository.save(job);

        UUID jobId = job.getId();
        try {
            uploadStorage.promote(incoming, jobId);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not stage the uploaded file", exception);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    uploadStorage.deleteStagedSource(jobId);
                }
            }
        });
        importJobScheduler.schedule(jobId);
        return new ImportStartResponse(jobId);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.strip();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.strip();
    }

    private static String limit(String value) {
        return value.length() <= MAX_METADATA_LENGTH ? value : value.substring(0, MAX_METADATA_LENGTH);
    }

    /** Keeps only a display-safe base name; it is never used to build a path. */
    static String displayFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String baseName = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        String safe = UNSAFE_FILENAME_CHARACTERS.matcher(baseName).replaceAll("").strip();
        return safe.isEmpty() ? null : limit(safe);
    }

    /** Returns the current status of an import job. Returns 404 if not found. */
    @Transactional(readOnly = true)
    public ImportJobResponse getJobStatus(UUID jobId) {
        TrackImportJobEntity job = jobRepository.findByIdWithTrack(jobId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ImportJobResponse.from(job, isPreviewAvailable(job));
    }

    private boolean isPreviewAvailable(TrackImportJobEntity job) {
        TrackEntity track = job.getTrack();
        return job.getStatus() == ImportJobStatus.DONE
                && track != null
                && track.getStatus() == TrackStatus.PENDING
                && track.getAudioObjectKey() != null
                && track.getAudioChecksum() != null
                && track.getAudioSizeBytes() != null
                && mediaStorage.exists(track.getAudioObjectKey());
    }

    private TrackEntity buildTrack(ImportTrackRequest request, String youtubeUrl) {
        TrackEntity track = new TrackEntity();
        track.setTitle(PENDING_METADATA);
        track.setArtist(PENDING_METADATA);
        track.setYoutubeUrl(youtubeUrl);
        track.setAudioUrl(null);
        track.setStatus(TrackStatus.PENDING);
        return track;
    }
}
