package com.blindtest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.blindtest.config.properties.ImportProperties;
import com.blindtest.infra.AudioTranscoder;
import com.blindtest.infra.YtDlpWrapper;
import com.blindtest.infra.WaveformGenerator;
import com.blindtest.model.dto.WsImportDoneEvent;
import com.blindtest.model.dto.WsImportFailedEvent;
import com.blindtest.model.dto.WsImportProgressEvent;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.ImportSource;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.StoredMedia;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Executes the yt-dlp download or uploaded-file transcode pipeline asynchronously for a queued import job. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportJobRunner {

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final String IMPORT_QUEUE_PREFIX = "/queue/import/";
    private static final String AUDIO_CONTENT_TYPE = "audio/mpeg";
    private static final long PROCESS_POLL_MILLIS = 250;
    private static final long TERMINATION_GRACE_SECONDS = 2;
    private static final long MAX_METADATA_BYTES = 1_048_576;
    private static final int MAX_PROVIDER_DIAGNOSTIC_LENGTH = 500;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern FFMPEG_PROGRESS_LINE = Pattern.compile("[a-z0-9_]+=.*");
    private static final String FFMPEG_OUT_TIME_KEY = "out_time_us=";
    private static final String OUTPUT_BASENAME = "audio";

    private final TrackImportJobRepository jobRepository;
    private final YtDlpWrapper ytDlpWrapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MediaStorage mediaStorage;
    private final ImportProperties importProperties;
    private final TrackMediaService trackMediaService;
    private final ObjectMapper objectMapper;
    private final WaveformGenerator waveformGenerator;
    private final AudioTranscoder audioTranscoder;
    private final ImportUploadStorage uploadStorage;

    /** Acquires and converts audio for the given job ID; updates status, progress, and pushes WS events. */
    public void execute(UUID jobId) {
        TrackImportJobEntity job = jobRepository.findByIdWithTrack(jobId).orElseThrow();
        if (job.getStatus() == ImportJobStatus.DONE) {
            return;
        }
        UUID adminId = job.getAdmin().getId();
        boolean upload = job.getSource() == ImportSource.UPLOAD;
        ProviderOutput providerOutput = upload
                ? new ProviderOutput("ffmpeg", ImportJobRunner::isFfmpegDiagnostic)
                : new ProviderOutput("yt-dlp", ImportJobRunner::isYtDlpDiagnostic);
        Path temporaryDirectory = null;
        try {
            job.setStatus(upload ? ImportJobStatus.PROCESSING : ImportJobStatus.DOWNLOADING);
            job.setErrorMessage(null);
            jobRepository.save(job);

            temporaryDirectory = createTemporaryDirectory(jobId);
            TrackEntity track = job.getTrack();
            Path audioFile;
            if (upload) {
                audioFile = transcodeUpload(job, temporaryDirectory, adminId, providerOutput);
            } else {
                audioFile = downloadFromYouTube(job, temporaryDirectory, adminId, providerOutput);
            }
            long audioSize = validateAudioFile(audioFile);
            String checksum = sha256(audioFile);
            if (!upload) {
                applyYouTubeMetadata(track, controlledOutputPath(
                        temporaryDirectory, temporaryDirectory.resolve(OUTPUT_BASENAME + ".info.json")));
            }
            track.setWaveformPeaks(objectMapper.writeValueAsString(
                    waveformGenerator.generate(audioFile, importProperties.timeout())));
            String objectKey = objectKey(track.getId(), checksum);
            StoredMedia storedMedia = mediaStorage.store(objectKey, audioFile, AUDIO_CONTENT_TYPE);
            verifyStoredMedia(storedMedia, objectKey, audioSize);

            trackMediaService.replaceMedia(track, objectKey, checksum, audioSize);

            deleteTemporaryDirectory(temporaryDirectory);
            job.setStatus(ImportJobStatus.DONE);
            job.setProgress((short) 100);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            messagingTemplate.convertAndSendToUser(adminId.toString(), IMPORT_QUEUE_PREFIX + jobId,
                    new WsImportDoneEvent(jobId, track.getId()));

        } catch (Exception e) {
            logImportFailure(jobId, e, providerOutput);
            job.setStatus(ImportJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            String msg = safeFailureMessage(e);
            job.setErrorMessage(msg);
            jobRepository.save(job);
            messagingTemplate.convertAndSendToUser(adminId.toString(), IMPORT_QUEUE_PREFIX + jobId,
                    new WsImportFailedEvent(jobId, msg));
        } finally {
            deleteTemporaryDirectoryQuietly(temporaryDirectory, jobId);
            if (upload) {
                // Upload jobs are terminal once they end here; a retry requires a new upload.
                uploadStorage.deleteStagedSource(jobId);
            }
        }
    }

    private Path downloadFromYouTube(
            TrackImportJobEntity job, Path temporaryDirectory, UUID adminId, ProviderOutput providerOutput)
            throws IOException, InterruptedException, ExecutionException {
        Path outputTemplate = temporaryDirectory.resolve(OUTPUT_BASENAME);
        Process process = ytDlpWrapper.start(job.getYoutubeUrl(), outputTemplate);
        int exitCode = awaitProcess(
                job, process, temporaryDirectory, adminId, providerOutput, ImportJobRunner::ytDlpProgress);
        if (exitCode != 0) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.PROVIDER);
        }

        job.setStatus(ImportJobStatus.PROCESSING);
        jobRepository.save(job);
        return controlledOutputPath(temporaryDirectory, Path.of(outputTemplate + ".mp3"));
    }

    private Path transcodeUpload(
            TrackImportJobEntity job, Path temporaryDirectory, UUID adminId, ProviderOutput providerOutput)
            throws IOException, InterruptedException, ExecutionException {
        Path source = uploadStorage.stagedSource(job.getId());
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.UPLOAD_MISSING);
        }
        Path output = temporaryDirectory.resolve(OUTPUT_BASENAME + ".mp3");
        Process process = audioTranscoder.start(source, output);
        int exitCode = awaitProcess(job, process, temporaryDirectory, adminId, providerOutput,
                ffmpegProgress(job.getTrack().getDurationSeconds()));
        if (exitCode != 0) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.TRANSCODE);
        }
        return controlledOutputPath(temporaryDirectory, output);
    }

    private int awaitProcess(
            TrackImportJobEntity job,
            Process process,
            Path temporaryDirectory,
            UUID adminId,
            ProviderOutput providerOutput,
            Function<String, OptionalDouble> progressParser)
            throws IOException, InterruptedException, ExecutionException {
        long deadline = System.nanoTime() + importProperties.timeout().toNanos();
        LinkedBlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
        try (ExecutorService outputReader = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> outputTask = outputReader.submit(
                    () -> readOutputLinesUnchecked(process.getInputStream(), outputLines));
            try {
                while (true) {
                    applyOutputLines(job, outputLines, adminId, providerOutput, progressParser);
                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0) {
                        terminateProcess(process);
                        throw new ImportJobFailureException(
                                ImportJobFailureException.Reason.TIMEOUT);
                    }
                    long waitMillis = Math.min(PROCESS_POLL_MILLIS,
                            Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                    if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS)) {
                        outputTask.get();
                        applyOutputLines(job, outputLines, adminId, providerOutput, progressParser);
                        return process.exitValue();
                    }
                    if (temporaryDirectorySize(temporaryDirectory) > importProperties.maxSizeBytes()) {
                        terminateProcess(process);
                        throw new ImportJobFailureException(
                                ImportJobFailureException.Reason.SIZE_LIMIT);
                    }
                }
            } finally {
                outputTask.cancel(true);
                if (process.isAlive()) {
                    terminateProcess(process);
                }
            }
        }
    }

    private void readOutputLinesUnchecked(InputStream inputStream, LinkedBlockingQueue<String> outputLines) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read yt-dlp output", exception);
        }
    }

    private void applyOutputLines(
            TrackImportJobEntity job,
            LinkedBlockingQueue<String> outputLines,
            UUID adminId,
            ProviderOutput providerOutput,
            Function<String, OptionalDouble> progressParser) {
        String line;
        while ((line = outputLines.poll()) != null) {
            providerOutput.observe(line);
            OptionalDouble percent = progressParser.apply(line);
            if (percent.isPresent()) {
                updateProgress(job, percent.getAsDouble(), adminId);
            }
        }
    }

    private static OptionalDouble ytDlpProgress(String line) {
        Matcher matcher = PROGRESS_PATTERN.matcher(line);
        return matcher.find() ? OptionalDouble.of(Double.parseDouble(matcher.group(1))) : OptionalDouble.empty();
    }

    /** Converts FFmpeg's {@code out_time_us} progress key into a percentage of the probed duration. */
    private static Function<String, OptionalDouble> ffmpegProgress(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return line -> OptionalDouble.empty();
        }
        double durationMicros = durationSeconds * 1_000_000d;
        return line -> {
            if (!line.startsWith(FFMPEG_OUT_TIME_KEY)) {
                return OptionalDouble.empty();
            }
            try {
                long outTimeMicros = Long.parseLong(line.substring(FFMPEG_OUT_TIME_KEY.length()).strip());
                return OptionalDouble.of(Math.max(0, outTimeMicros) * 100d / durationMicros);
            } catch (NumberFormatException exception) {
                return OptionalDouble.empty();
            }
        };
    }

    private static boolean isYtDlpDiagnostic(String line) {
        return line.regionMatches(true, 0, "ERROR:", 0, "ERROR:".length());
    }

    /** FFmpeg progress lines are {@code key=value}; anything else it prints at error level is a diagnostic. */
    private static boolean isFfmpegDiagnostic(String line) {
        return !line.isBlank() && !FFMPEG_PROGRESS_LINE.matcher(line).matches();
    }

    private long temporaryDirectorySize(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            long total = 0;
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                total = Math.addExact(total, Files.size(path));
                if (total > importProperties.maxSizeBytes()) {
                    return total;
                }
            }
            return total;
        }
    }

    private void terminateProcess(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        if (!process.waitFor(TERMINATION_GRACE_SECONDS, TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private Path createTemporaryDirectory(UUID jobId) throws IOException {
        Path temporaryRoot = importProperties.tempDir().toAbsolutePath().normalize();
        Files.createDirectories(temporaryRoot);
        return Files.createTempDirectory(temporaryRoot, jobId + "-").normalize();
    }

    private Path controlledOutputPath(Path temporaryDirectory, Path candidate) {
        Path normalizedDirectory = temporaryDirectory.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedDirectory)
                || Files.isSymbolicLink(normalizedCandidate)) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.INVALID_MEDIA);
        }
        return normalizedCandidate;
    }

    private long validateAudioFile(Path audioFile) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                audioFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.INVALID_MEDIA);
        }
        long size = attributes.size();
        if (size == 0) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.INVALID_MEDIA);
        }
        if (size > importProperties.maxSizeBytes()) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.SIZE_LIMIT);
        }
        byte[] header;
        try (InputStream input = Files.newInputStream(audioFile)) {
            header = input.readNBytes(3);
        }
        boolean id3 = header.length == 3 && header[0] == 'I' && header[1] == 'D' && header[2] == '3';
        boolean mpegFrame = header.length >= 2
                && (header[0] & 0xff) == 0xff
                && (header[1] & 0xe0) == 0xe0;
        if (!id3 && !mpegFrame) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.INVALID_MEDIA);
        }
        return size;
    }

    private String sha256(Path audioFile) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(audioFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void applyYouTubeMetadata(TrackEntity track, Path metadataFile) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                metadataFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() <= 0
                || attributes.size() > MAX_METADATA_BYTES) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.INVALID_MEDIA);
        }
        JsonNode metadata = objectMapper.readTree(metadataFile.toFile());
        String title = text(metadata, "title");
        String artist = firstText(metadata, "artist", "uploader", "channel");
        if (title == null || artist == null) {
            throw new ImportJobFailureException(ImportJobFailureException.Reason.INVALID_MEDIA);
        }
        track.setTitle(limit(title, 255));
        track.setArtist(limit(artist, 255));
        JsonNode duration = metadata.get("duration");
        if (duration != null && duration.isNumber() && duration.doubleValue() > 0) {
            track.setDurationSeconds((int) Math.floor(duration.doubleValue()));
        }
        String thumbnail = text(metadata, "thumbnail");
        if (thumbnail != null && thumbnail.length() <= 500) {
            track.setImageUrl(thumbnail);
        }
    }

    private String firstText(JsonNode metadata, String... fields) {
        for (String field : fields) {
            String value = text(metadata, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode metadata, String field) {
        JsonNode value = metadata.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue().strip();
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String objectKey(UUID trackId, String checksum) {
        return "tracks/" + trackId + "/audio-" + checksum + ".mp3";
    }

    private void verifyStoredMedia(StoredMedia storedMedia, String expectedKey, long expectedSize) {
        if (!expectedKey.equals(storedMedia.objectKey()) || storedMedia.size() != expectedSize) {
            throw new IllegalStateException("Storage returned inconsistent audio metadata");
        }
        if (!mediaStorage.exists(expectedKey)) {
            throw new IllegalStateException("Stored audio object could not be confirmed");
        }
    }

    private void deleteTemporaryDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void deleteTemporaryDirectoryQuietly(Path directory, UUID jobId) {
        try {
            deleteTemporaryDirectory(directory);
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not fully clean temporary files for import job {}", jobId);
        }
    }

    private String safeFailureMessage(Exception exception) {
        if (exception instanceof ImportJobFailureException importFailure) {
            return importFailure.safeMessage();
        }
        return "The media import failed";
    }

    private void logImportFailure(UUID jobId, Exception exception, ProviderOutput providerOutput) {
        String diagnostic = providerOutput.diagnostic();
        if (diagnostic == null) {
            log.error("Import job {} failed with {}", jobId, exception.getClass().getSimpleName());
            return;
        }
        log.error(
                "Import job {} failed with {}: {}={}",
                jobId,
                exception.getClass().getSimpleName(),
                providerOutput.label(),
                diagnostic);
    }

    private static final class ProviderOutput {

        private final String label;
        private final Predicate<String> isDiagnostic;
        private String diagnostic;

        private ProviderOutput(String label, Predicate<String> isDiagnostic) {
            this.label = label;
            this.isDiagnostic = isDiagnostic;
        }

        private void observe(String line) {
            if (!isDiagnostic.test(line)) {
                return;
            }
            String safeLine = URL_PATTERN.matcher(line).replaceAll("[url]").
                    replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").
                    trim();
            diagnostic = safeLine.substring(0, Math.min(safeLine.length(), MAX_PROVIDER_DIAGNOSTIC_LENGTH));
        }

        private String label() {
            return label;
        }

        private String diagnostic() {
            return diagnostic;
        }
    }

    private void updateProgress(TrackImportJobEntity job, double percent, UUID adminId) {
        short progress = (short) Math.min((int) percent, 99);
        if (progress == job.getProgress()) {
            return;
        }
        job.setProgress(progress);
        jobRepository.save(job);
        messagingTemplate.convertAndSendToUser(adminId.toString(), IMPORT_QUEUE_PREFIX + job.getId(),
                new WsImportProgressEvent(job.getId(), job.getStatus().name(), job.getProgress()));
    }
}
