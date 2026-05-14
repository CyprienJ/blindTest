package com.blindtest.service;

import com.blindtest.infra.YtDlpWrapper;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Executes the yt-dlp download pipeline asynchronously for a queued import job. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportJobRunner {

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)%");

    private final TrackImportJobRepository jobRepository;
    private final TrackRepository trackRepository;
    private final YtDlpWrapper ytDlpWrapper;

    @Value("${app.media-dir:/media}")
    private String mediaDir;

    /** Downloads and converts audio for the given job ID; updates status and progress in DB. */
    @Async
    public void execute(UUID jobId) {
        TrackImportJobEntity job = jobRepository.findByIdWithTrack(jobId).orElseThrow();
        try {
            job.setStatus(ImportJobStatus.DOWNLOADING);
            jobRepository.save(job);

            Path outputTemplate = Path.of(mediaDir, jobId.toString());
            Files.createDirectories(outputTemplate.getParent());

            Process process = ytDlpWrapper.start(job.getYoutubeUrl(), outputTemplate);
            parseOutputLines(job, process.getInputStream());

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

        } catch (Exception e) {
            log.error("Import job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(ImportJobStatus.FAILED);
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            job.setErrorMessage(msg);
            jobRepository.save(job);
        }
    }

    private void parseOutputLines(TrackImportJobEntity job, InputStream inputStream)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                updateProgress(job, line);
            }
        }
    }

    private void updateProgress(TrackImportJobEntity job, String line) {
        Matcher matcher = PROGRESS_PATTERN.matcher(line);
        if (matcher.find()) {
            double pct = Double.parseDouble(matcher.group(1));
            job.setProgress((short) Math.min((int) pct, 99));
            jobRepository.save(job);
        }
    }
}
