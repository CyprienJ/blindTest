package com.blindtest.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.infra.YtDlpWrapper;
import com.blindtest.model.dto.WsImportDoneEvent;
import com.blindtest.model.dto.WsImportFailedEvent;
import com.blindtest.model.dto.WsImportProgressEvent;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ImportJobRunnerTest {

    private TrackImportJobRepository jobRepository;
    private TrackRepository trackRepository;
    private YtDlpWrapper ytDlpWrapper;
    private SimpMessagingTemplate messagingTemplate;
    private ImportJobRunner runner;

    @BeforeEach
    void setUp() {
        jobRepository = mock(TrackImportJobRepository.class);
        trackRepository = mock(TrackRepository.class);
        ytDlpWrapper = mock(YtDlpWrapper.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        runner = new ImportJobRunner(jobRepository, trackRepository, ytDlpWrapper, messagingTemplate);
        ReflectionTestUtils.setField(runner, "mediaDir", "/tmp");
    }

    @Test
    void executePublishesProgressAndDoneEvents() throws IOException, InterruptedException {
        UUID jobId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();

        TrackImportJobEntity job = buildJob(jobId, adminId, trackId);
        when(jobRepository.findByIdWithTrack(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenReturn(job);
        when(trackRepository.save(any())).thenReturn(job.getTrack());

        Process process = mock(Process.class);
        String output = "50.0%\n100.0%\n";
        when(process.getInputStream()).thenReturn(
                new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor()).thenReturn(0);
        when(ytDlpWrapper.start(any(), any())).thenReturn(process);

        runner.execute(jobId);

        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(adminId.toString()),
                eq("/queue/import/" + jobId),
                any(WsImportProgressEvent.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq(adminId.toString()),
                eq("/queue/import/" + jobId),
                any(WsImportDoneEvent.class));
    }

    @Test
    void executePublishesFailedEventOnProcessError() throws IOException, InterruptedException {
        UUID jobId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        TrackImportJobEntity job = buildJob(jobId, adminId, UUID.randomUUID());
        when(jobRepository.findByIdWithTrack(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenReturn(job);

        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenReturn(1);
        when(ytDlpWrapper.start(any(), any())).thenReturn(process);

        runner.execute(jobId);

        verify(messagingTemplate).convertAndSendToUser(
                eq(adminId.toString()),
                eq("/queue/import/" + jobId),
                any(WsImportFailedEvent.class));
    }

    private TrackImportJobEntity buildJob(UUID jobId, UUID adminId, UUID trackId) {
        UserEntity admin = new UserEntity();
        ReflectionTestUtils.setField(admin, "id", adminId);

        TrackEntity track = new TrackEntity();
        ReflectionTestUtils.setField(track, "id", trackId);
        track.setAudioUrl("");

        TrackImportJobEntity job = new TrackImportJobEntity();
        ReflectionTestUtils.setField(job, "id", jobId);
        job.setYoutubeUrl("https://youtube.com/watch?v=test");
        job.setAdmin(admin);
        job.setTrack(track);
        return job;
    }
}
