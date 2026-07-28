package com.blindtest.service;

import com.blindtest.infra.YtDlpWrapper;
import com.blindtest.model.dto.WsImportDoneEvent;
import com.blindtest.model.dto.WsImportFailedEvent;
import com.blindtest.model.dto.WsImportProgressEvent;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Executes the yt-dlp download pipeline asynchronously for a queued import job. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportJobRunner {

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final String IMPORT_QUEUE_PREFIX = "/queue/import/";

    private final TrackImportJobRepository jobRepository;
    private final TrackRepository trackRepository;
    private final YtDlpWrapper ytDlpWrapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.media-dir:/media}")
    private String mediaDir;

    /** Downloads and converts audio for the given job ID; updates status, progress, and pushes WS events. */
    public void execute(UUID jobId) {
        TrackImportJobEntity job = jobRepository.findByIdWithTrack(jobId).orElseThrow();
        UUID adminId = job.getAdmin().getId();
        try {
            job.setStatus(ImportJobStatus.DOWNLOADING);
            jobRepository.save(job);

            Path outputTemplate = Path.of(mediaDir, jobId.toString());
            Files.createDirectories(outputTemplate.getParent());

            Process process = ytDlpWrapper.start(job.getYoutubeUrl(), outputTemplate);
            parseOutputLines(job, process.getInputStream(), adminId);

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("yt-dlp failed with exit code " + exitCode);
            }

            job.setStatus(ImportJobStatus.PROCESSING);
            jobRepository.save(job);

            TrackEntity track = job.getTrack();
            track.setAudioUrl("/media/" + jobId + ".mp3");
            track.setStatus(TrackStatus.PENDING);
            trackRepository.save(track);

            job.setStatus(ImportJobStatus.DONE);
            job.setProgress((short) 100);
            jobRepository.save(job);

            messagingTemplate.convertAndSendToUser(adminId.toString(), IMPORT_QUEUE_PREFIX + jobId,
                    new WsImportDoneEvent(jobId, track.getId(), track.getAudioUrl()));

        } catch (Exception e) {
            log.error("Import job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(ImportJobStatus.FAILED);
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            job.setErrorMessage(msg);
            jobRepository.save(job);
            messagingTemplate.convertAndSendToUser(adminId.toString(), IMPORT_QUEUE_PREFIX + jobId,
                    new WsImportFailedEvent(jobId, msg));
        }
    }

    private void parseOutputLines(TrackImportJobEntity job, InputStream inputStream, UUID adminId)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                updateProgress(job, line, adminId);
            }
        }
    }

    private void updateProgress(TrackImportJobEntity job, String line, UUID adminId) {
        Matcher matcher = PROGRESS_PATTERN.matcher(line);
        if (matcher.find()) {
            double pct = Double.parseDouble(matcher.group(1));
            job.setProgress((short) Math.min((int) pct, 99));
            jobRepository.save(job);
            messagingTemplate.convertAndSendToUser(adminId.toString(), IMPORT_QUEUE_PREFIX + job.getId(),
                    new WsImportProgressEvent(job.getId(), job.getStatus().name(), job.getProgress()));
        }
    }
}
