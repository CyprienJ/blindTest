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
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.MediaStorageException;
import com.blindtest.service.port.StoredMedia;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(OutputCaptureExtension.class)
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
    private WaveformGenerator waveformGenerator;
    private AudioTranscoder audioTranscoder;
    private ImportJobRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        jobRepository = mock(TrackImportJobRepository.class);
        trackRepository = mock(TrackRepository.class);
        ytDlpWrapper = mock(YtDlpWrapper.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        mediaStorage = mock(MediaStorage.class);
        trackMediaService = mock(TrackMediaService.class);
        waveformGenerator = mock(WaveformGenerator.class);
        audioTranscoder = mock(AudioTranscoder.class);
        when(waveformGenerator.generate(any(), any())).thenReturn(java.util.List.of(0, 25, 100));
        doAnswer(invocation -> {
            TrackEntity target = invocation.getArgument(0);
            target.setAudioUrl(null);
            target.setAudioObjectKey(invocation.getArgument(1));
            target.setAudioChecksum(invocation.getArgument(2));
            target.setAudioSizeBytes(invocation.getArgument(3));
            trackRepository.save(target);
            return null;
        }).when(trackMediaService).replaceMedia(any(), any(), any(), any(Long.class));
        runner = newRunner(new ImportProperties(tempRoot, 8, Duration.ofMinutes(1), null));
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
        assertThat(track.getTitle()).isEqualTo("YouTube title");
        assertThat(track.getArtist()).isEqualTo("YouTube channel");
        assertThat(track.getDurationSeconds()).isEqualTo(185);
        assertThat(track.getImageUrl()).isEqualTo("https://images.example.test/cover.jpg");
        assertThat(track.getWaveformPeaks()).isEqualTo("[0,25,100]");
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
        runner = newRunner(new ImportProperties(tempRoot, maxSize, Duration.ofMinutes(1), null));
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
    void processFailureLogsBoundedProviderDiagnosticWithoutUrl(CapturedOutput output) throws Exception {
        TestJob testJob = arrangeJob();
        arrangeProcess(
                VALID_MP3,
                1,
                "ERROR: [youtube] blocked request https://provider.example/watch?token=secret\n");

        runner.execute(testJob.jobId());

        assertThat(output).
                contains("yt-dlp=ERROR: [youtube] blocked request [url]").
                doesNotContain("token=secret");
        assertThat(testJob.job().getErrorMessage()).
                isEqualTo("The media provider could not complete the import");
    }

    @Test
    void timedOutProcessIsTerminatedAndJobFails() throws Exception {
        TestJob testJob = arrangeJob();
        runner = newRunner(new ImportProperties(tempRoot, 8, Duration.ofMillis(1), null));
        Process process = arrangeRunningProcess(new byte[0]);

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getErrorMessage()).contains("timed out");
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
        assertThat(testJob.job().getErrorMessage()).contains("maximum allowed size");
        verify(process).destroy();
        verify(mediaStorage, never()).store(any(), any(), any());
        assertTemporaryRootIsEmpty();
    }

    @Test
    void providerFailureIsSanitizedBeforePersistenceAndNotification() throws Exception {
        TestJob testJob = arrangeJob();
        when(ytDlpWrapper.start(any(), any())).
                thenThrow(new IOException("secret provider response and local path"));

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getErrorMessage()).isEqualTo("The media import failed");
        assertThat(testJob.job().getErrorMessage()).doesNotContain("secret", "local path");
        ArgumentCaptor<WsImportFailedEvent> event =
                ArgumentCaptor.forClass(WsImportFailedEvent.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(testJob.adminId().toString()),
                eq("/queue/import/" + testJob.jobId()),
                event.capture());
        assertThat(event.getValue().getErrorMessage()).isEqualTo("The media import failed");
        assertTemporaryRootIsEmpty();
    }

    @Test
    void symbolicLinkOutputCannotEscapeTheJobTemporaryDirectory() throws Exception {
        TestJob testJob = arrangeJob();
        Path outsideAudio = tempRoot.resolve("outside.mp3");
        Files.write(outsideAudio, VALID_MP3);
        when(ytDlpWrapper.start(any(), any())).thenAnswer(invocation -> {
            Path outputTemplate = invocation.getArgument(1);
            Files.createSymbolicLink(Path.of(outputTemplate + ".mp3"), outsideAudio);
            Process process = mock(Process.class);
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
            when(process.exitValue()).thenReturn(0);
            return process;
        });

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getErrorMessage()).contains("not a valid MP3");
        assertThat(Files.readAllBytes(outsideAudio)).isEqualTo(VALID_MP3);
        verify(mediaStorage, never()).store(any(), any(), any());
        Files.delete(outsideAudio);
        assertTemporaryRootIsEmpty();
    }

    @Test
    void uploadJobTranscodesStagedSourceKeepsRequestMetadataAndDeletesSource() throws Exception {
        TestJob testJob = arrangeUploadJob();
        Path source = stageUpload(testJob.jobId());
        arrangeTranscode(VALID_MP3, 0, "out_time_us=90000000\nprogress=continue\nprogress=end\n");
        arrangeSuccessfulStorage();

        runner.execute(testJob.jobId());

        TrackEntity track = testJob.job().getTrack();
        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(track.getTitle()).isEqualTo("Uploaded title");
        assertThat(track.getArtist()).isEqualTo("Uploaded artist");
        assertThat(track.getDurationSeconds()).isEqualTo(180);
        assertThat(track.getImageUrl()).isNull();
        assertThat(track.getAudioSizeBytes()).isEqualTo(VALID_MP3.length);
        verify(audioTranscoder).start(eq(source), any(Path.class));
        verify(ytDlpWrapper, never()).start(any(), any());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(testJob.adminId().toString()),
                eq("/queue/import/" + testJob.jobId()),
                events.capture());
        assertThat(events.getAllValues()).filteredOn(WsImportProgressEvent.class::isInstance).
                singleElement().
                satisfies(event -> {
                    WsImportProgressEvent progress = (WsImportProgressEvent) event;
                    assertThat(progress.getStatus()).isEqualTo("PROCESSING");
                    assertThat(progress.getProgress()).isEqualTo(50);
                });
        assertThat(source).doesNotExist();
        assertTemporaryWorkspaceHoldsNoFiles();
    }

    @Test
    void uploadJobWithoutStagedSourceFailsWithoutStartingFfmpeg() throws Exception {
        TestJob testJob = arrangeUploadJob();

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getErrorMessage()).
                isEqualTo("The uploaded file is no longer available; upload it again");
        verify(audioTranscoder, never()).start(any(), any());
        verify(mediaStorage, never()).store(any(), any(), any());
        assertTemporaryWorkspaceHoldsNoFiles();
    }

    @Test
    void failedTranscodeMarksJobFailedLogsDiagnosticAndDeletesSource(CapturedOutput output) throws Exception {
        TestJob testJob = arrangeUploadJob();
        Path source = stageUpload(testJob.jobId());
        arrangeTranscode(new byte[0], 1, "progress=continue\nError while decoding stream #0:0\n");

        runner.execute(testJob.jobId());

        assertThat(testJob.job().getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(testJob.job().getErrorMessage()).isEqualTo("The uploaded audio could not be converted");
        assertThat(output).contains("ffmpeg=Error while decoding stream #0:0");
        verify(mediaStorage, never()).store(any(), any(), any());
        assertThat(source).doesNotExist();
        assertTemporaryWorkspaceHoldsNoFiles();
    }

    private TestJob arrangeUploadJob() {
        TestJob testJob = arrangeJob();
        TrackImportJobEntity job = testJob.job();
        job.setSource(ImportSource.UPLOAD);
        job.setYoutubeUrl(null);
        job.getTrack().setTitle("Uploaded title");
        job.getTrack().setArtist("Uploaded artist");
        job.getTrack().setDurationSeconds(180);
        return testJob;
    }

    private Path stageUpload(UUID jobId) throws IOException {
        Path source = new ImportUploadStorage(new ImportProperties(tempRoot, 8, Duration.ofMinutes(1), null)).
                stagedSource(jobId);
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[] {'I', 'D', '3', 0});
        return source;
    }

    private void arrangeTranscode(byte[] content, int exitCode, String output) throws Exception {
        when(audioTranscoder.start(any(), any())).thenAnswer(invocation -> {
            Files.write(invocation.getArgument(1, Path.class), content);
            Process process = mock(Process.class);
            when(process.getInputStream()).thenReturn(
                    new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
            when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
            when(process.exitValue()).thenReturn(exitCode);
            return process;
        });
    }

    private void assertTemporaryWorkspaceHoldsNoFiles() throws IOException {
        try (Stream<Path> contents = Files.walk(tempRoot)) {
            assertThat(contents.filter(Files::isRegularFile)).isEmpty();
        }
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
        arrangeProcess(content, exitCode, "50.0%\n100.0%\n");
    }

    private void arrangeProcess(byte[] content, int exitCode, String output) throws Exception {
        when(ytDlpWrapper.start(any(), any())).thenAnswer(invocation -> {
            Path outputTemplate = invocation.getArgument(1);
            Files.write(Path.of(outputTemplate + ".mp3"), content);
            Files.writeString(Path.of(outputTemplate + ".info.json"), """
                    {
                      "title": "YouTube title",
                      "artist": null,
                      "uploader": "YouTube channel",
                      "duration": 185.9,
                      "thumbnail": "https://images.example.test/cover.jpg"
                    }
                    """);
            Process process = mock(Process.class);
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

    private ImportJobRunner newRunner(ImportProperties properties) {
        return new ImportJobRunner(
                jobRepository,
                ytDlpWrapper,
                messagingTemplate,
                mediaStorage,
                properties,
                trackMediaService,
                new ObjectMapper(),
                waveformGenerator,
                audioTranscoder,
                new ImportUploadStorage(properties));
    }

    private TrackImportJobEntity buildJob(UUID jobId, UUID adminId, UUID trackId) {
        UserEntity admin = new UserEntity();
        ReflectionTestUtils.setField(admin, "id", adminId);

        TrackEntity track = new TrackEntity();
        ReflectionTestUtils.setField(track, "id", trackId);

        TrackImportJobEntity job = new TrackImportJobEntity();
        ReflectionTestUtils.setField(job, "id", jobId);
        job.setYoutubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
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
