package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.config.properties.ImportProperties;
import com.blindtest.infra.YtDlpWrapper;
import com.blindtest.model.dto.WsImportDoneEvent;
import com.blindtest.model.dto.WsImportFailedEvent;
import com.blindtest.model.dto.WsImportProgressEvent;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.MediaStorageException;
import com.blindtest.service.port.StoredMedia;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class ImportJobRunnerTest {

    private static final byte[] VALID_MP3 = {(byte) 0xff, (byte) 0xfb, 1, 2};

    @TempDir
    private Path tempRoot;

    private TrackImportJobRepository jobRepository;
    private TrackRepository trackRepository;
    private YtDlpWrapper ytDlpWrapper;
    private SimpMessagingTemplate messagingTemplate;
    private MediaStorage mediaStorage;
    private TrackMediaService trackMediaService;
    private ImportJobRunner runner;

    @BeforeEach
    void setUp() {
        jobRepository = mock(TrackImportJobRepository.class);
        trackRepository = mock(TrackRepository.class);
        ytDlpWrapper = mock(YtDlpWrapper.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        mediaStorage = mock(MediaStorage.class);
        trackMediaService = mock(TrackMediaService.class);
        doAnswer(invocation -> {
            TrackEntity target = invocation.getArgument(0);
            target.setAudioUrl(null);
            target.setAudioObjectKey(invocation.getArgument(1));
            target.setAudioChecksum(invocation.getArgument(2));
            target.setAudioSizeBytes(invocation.getArgument(3));
            trackRepository.save(target);
            return null;
        }).when(trackMediaService).replaceMedia(any(), any(), any(), any(Long.class));
        runner = new ImportJobRunner(
                jobRepository,
                ytDlpWrapper,
                messagingTemplate,
                mediaStorage,
                new ImportProperties(tempRoot, 8, Duration.ofMinutes(1)),
                trackMediaService);
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(trackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void executeUploadsVerifiedAudioPersistsMetadataAndCleansTemporaryFiles() throws Exception {
        TestJob testJob = arrangeJob();
        arrangeProcess(VALID_MP3, 0);
        arrangeSuccessfulStorage();

        runner.execute(testJob.jobId());

        TrackEntity track = testJob.job().getTrack();
        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(testJob.job().getProgress()).isEqualTo((short) 100);
        assertThat(track.getAudioUrl()).isNull();
        assertThat(track.getAudioChecksum()).hasSize(64);
        assertThat(track.getAudioSizeBytes()).isEqualTo(VALID_MP3.length);
        assertThat(track.getAudioObjectKey()).
                isEqualTo("tracks/" + testJob.trackId() + "/audio-" + track.getAudioChecksum() + ".mp3");
        verify(mediaStorage).store(eq(track.getAudioObjectKey()), any(Path.class), eq("audio/mpeg"));
        verify(mediaStorage).exists(track.getAudioObjectKey());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(testJob.adminId().toString()),
                eq("/queue/import/" + testJob.jobId()),
                events.capture());
        assertThat(events.getAllValues()).anyMatch(WsImportProgressEvent.class::isInstance);
        assertThat(events.getAllValues()).filteredOn(WsImportDoneEvent.class::isInstance).
                singleElement().
                satisfies(event -> {
                    WsImportDoneEvent doneEvent = (WsImportDoneEvent) event;
                    assertThat(doneEvent.getTrackId()).isEqualTo(testJob.trackId());
                });
        InOrder persistenceOrder = inOrder(trackRepository, jobRepository);
        persistenceOrder.verify(trackRepository).save(track);
        persistenceOrder.verify(jobRepository).save(testJob.job());
        assertTemporaryRootIsEmpty();
    }

    @Test
    void storageFailureMarksJobFailedWithoutPersistingTrackOrDoneEvent() throws Exception {
        TestJob testJob = arrangeJob();
        arrangeProcess(VALID_MP3, 0);
        when(mediaStorage.store(any(), any(), any())).thenThrow(
                new MediaStorageException("store", new IOException("storage unavailable")));

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getTrack().getAudioObjectKey()).isNull();
        verify(trackRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSendToUser(
                any(), any(), any(WsImportDoneEvent.class));
        verify(messagingTemplate).convertAndSendToUser(
                eq(testJob.adminId().toString()),
                eq("/queue/import/" + testJob.jobId()),
                any(WsImportFailedEvent.class));
        assertTemporaryRootIsEmpty();
    }

    @Test
    void missingStoredObjectMarksJobFailed() throws Exception {
        TestJob testJob = arrangeJob();
        arrangeProcess(VALID_MP3, 0);
        arrangeStoredMediaResult();
        when(mediaStorage.exists(any())).thenReturn(false);

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        verify(trackRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSendToUser(
                any(), any(), any(WsImportDoneEvent.class));
        assertTemporaryRootIsEmpty();
    }

    @Test
    void retryUsesTheSameContentAddressedObjectKey() throws Exception {
        TestJob testJob = arrangeJob();
        arrangeProcess(VALID_MP3, 0);
        when(mediaStorage.store(any(), any(), any())).
                thenThrow(new MediaStorageException("store", new IOException("transient"))).
                thenAnswer(invocation -> storedMedia(invocation.getArgument(0), invocation.getArgument(1)));
        when(mediaStorage.exists(any())).thenReturn(true);

        runner.execute(testJob.jobId());
        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        runner.execute(testJob.jobId());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(mediaStorage, times(2)).store(keys.capture(), any(Path.class), eq("audio/mpeg"));
        assertThat(keys.getAllValues()).hasSize(2).allMatch(keys.getAllValues().getFirst()::equals);
        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.DONE);
        verify(ytDlpWrapper, times(2)).start(any(), any());
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(testJob.adminId().toString()),
                eq("/queue/import/" + testJob.jobId()),
                any(WsImportDoneEvent.class));
        assertTemporaryRootIsEmpty();
    }

    @Test
    void completedJobIsNotProcessedAgain() throws Exception {
        TestJob testJob = arrangeJob();
        testJob.job().setStatus(ImportJobStatus.DONE);

        runner.execute(testJob.jobId());

        verify(ytDlpWrapper, never()).start(any(), any());
        verify(mediaStorage, never()).store(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidAudio")
    void invalidAudioMarksJobFailedAndIsNeverUploaded(byte[] content, long maxSize) throws Exception {
        TestJob testJob = arrangeJob();
        runner = new ImportJobRunner(
                jobRepository,
                ytDlpWrapper,
                messagingTemplate,
                mediaStorage,
                new ImportProperties(tempRoot, maxSize, Duration.ofMinutes(1)),
                trackMediaService);
        arrangeProcess(content, 0);

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        verify(mediaStorage, never()).store(any(), any(), any());
        assertTemporaryRootIsEmpty();
    }

    @Test
    void processFailureMarksJobFailedAndCleansTemporaryFiles() throws Exception {
        TestJob testJob = arrangeJob();
        arrangeProcess(VALID_MP3, 1);

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        verify(mediaStorage, never()).store(any(), any(), any());
        assertTemporaryRootIsEmpty();
    }

    @Test
    void timedOutProcessIsTerminatedAndJobFails() throws Exception {
        TestJob testJob = arrangeJob();
        runner = new ImportJobRunner(
                jobRepository,
                ytDlpWrapper,
                messagingTemplate,
                mediaStorage,
                new ImportProperties(tempRoot, 8, Duration.ofMillis(1)),
                trackMediaService);
        Process process = arrangeRunningProcess(new byte[0]);

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getErrorMessage()).contains("timeout");
        verify(process).destroy();
        verify(mediaStorage, never()).store(any(), any(), any());
        assertTemporaryRootIsEmpty();
    }

    @Test
    void growingImportBeyondLimitIsTerminatedBeforeProcessCompletes() throws Exception {
        TestJob testJob = arrangeJob();
        Process process = arrangeRunningProcess(new byte[9]);

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getErrorMessage()).contains("maximum size");
        verify(process).destroy();
        verify(mediaStorage, never()).store(any(), any(), any());
        assertTemporaryRootIsEmpty();
    }

    private TestJob arrangeJob() {
        UUID jobId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        TrackImportJobEntity job = buildJob(jobId, adminId, trackId);
        when(jobRepository.findByIdWithTrack(jobId)).thenReturn(Optional.of(job));
        return new TestJob(jobId, adminId, trackId, job);
    }

    private void arrangeProcess(byte[] content, int exitCode) throws Exception {
        when(ytDlpWrapper.start(any(), any())).thenAnswer(invocation -> {
            Path outputTemplate = invocation.getArgument(1);
            Files.write(Path.of(outputTemplate + ".mp3"), content);
            Process process = mock(Process.class);
            String output = "50.0%\n100.0%\n";
            when(process.getInputStream()).thenReturn(
                    new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
            when(process.exitValue()).thenReturn(exitCode);
            return process;
        });
    }

    private Process arrangeRunningProcess(byte[] content) throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenAnswer(invocation ->
                invocation.getArgument(1) == TimeUnit.SECONDS);
        when(ytDlpWrapper.start(any(), any())).thenAnswer(invocation -> {
            Path outputTemplate = invocation.getArgument(1);
            if (content.length > 0) {
                Files.write(Path.of(outputTemplate + ".part"), content);
            }
            return process;
        });
        return process;
    }

    private void arrangeSuccessfulStorage() {
        arrangeStoredMediaResult();
        when(mediaStorage.exists(any())).thenReturn(true);
    }

    private void arrangeStoredMediaResult() {
        when(mediaStorage.store(any(), any(), any())).thenAnswer(invocation ->
                storedMedia(invocation.getArgument(0), invocation.getArgument(1)));
    }

    private StoredMedia storedMedia(String objectKey, Path source) throws IOException {
        return new StoredMedia(objectKey, Files.size(source), "test-etag");
    }

    private void assertTemporaryRootIsEmpty() throws IOException {
        try (Stream<Path> contents = Files.list(tempRoot)) {
            assertThat(contents).isEmpty();
        }
    }

    private TrackImportJobEntity buildJob(UUID jobId, UUID adminId, UUID trackId) {
        UserEntity admin = new UserEntity();
        ReflectionTestUtils.setField(admin, "id", adminId);

        TrackEntity track = new TrackEntity();
        ReflectionTestUtils.setField(track, "id", trackId);

        TrackImportJobEntity job = new TrackImportJobEntity();
        ReflectionTestUtils.setField(job, "id", jobId);
        job.setYoutubeUrl("https://youtube.com/watch?v=test");
        job.setStatus(ImportJobStatus.QUEUED);
        job.setAdmin(admin);
        job.setTrack(track);
        return job;
    }

    private static Stream<Arguments> invalidAudio() {
        return Stream.of(
                Arguments.of(new byte[0], 8L),
                Arguments.of(new byte[] {1, 2, 3}, 8L),
                Arguments.of(new byte[] {(byte) 0xff, (byte) 0xfb, 1, 2}, 3L));
    }

    private record TestJob(UUID jobId, UUID adminId, UUID trackId, TrackImportJobEntity job) {
    }
}
