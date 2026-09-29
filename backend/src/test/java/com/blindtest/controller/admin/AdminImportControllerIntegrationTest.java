package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.infra.AudioProbe;
import com.blindtest.infra.ProbedAudio;
import com.blindtest.model.entity.ImportSource;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.ImportUploadStorage;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.ImportJobScheduler;
import com.blindtest.service.port.MediaStorage;
import java.time.LocalDateTime;
import com.blindtest.service.port.TurnstileVerifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:adminimport;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AdminImportControllerIntegrationTest {

    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final String WATCH_URL = "https://www.youtube.com/watch?v=" + VIDEO_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private TrackImportJobRepository jobRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ImportJobScheduler importJobScheduler;

    @MockBean
    private MediaStorage mediaStorage;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    @MockBean
    private AudioProbe audioProbe;

    @Autowired
    private ImportUploadStorage uploadStorage;

    private String adminToken;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity admin = new UserEntity();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);
    }

    @Test
    void startImportRequiresAdminRole() throws Exception {
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\"}";

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        content(body)).
                andExpect(status().isUnauthorized());

        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, userToken).
                        content(body)).
                andExpect(status().isForbidden());
    }

    @Test
    void startImportReturns202WithJobId() throws Exception {
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\"}";

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted()).
                andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    void startImportDefersMetadataExtractionToTheWorker() throws Exception {
        String body = """
                {
                  "youtubeUrl": "https://youtu.be/dQw4w9WgXcQ"
                }
                """;

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted());

        var track = trackRepository.findAll().getFirst();
        assertThat(track.getTitle()).isEqualTo("Pending YouTube metadata");
        assertThat(track.getArtist()).isEqualTo("Pending YouTube metadata");
        assertThat(track.getDurationSeconds()).isNull();
        var job = jobRepository.findAll().getFirst();
        assertThat(job.getYoutubeVideoId()).isEqualTo(VIDEO_ID);
        assertThat(job.getYoutubeUrl()).isEqualTo(WATCH_URL);
        assertThat(track.getYoutubeUrl()).isEqualTo(WATCH_URL);
    }

    @Test
    void getJobStatusReturnsQueuedJob() throws Exception {
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\"}";

        String response = mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted()).
                andReturn().getResponse().getContentAsString();

        String jobId = response.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/admin/tracks/import/" + jobId).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("QUEUED")).
                andExpect(jsonPath("$.jobId").value(jobId)).
                andExpect(jsonPath("$.progress").value(0)).
                andExpect(jsonPath("$.previewAvailable").value(false)).
                andExpect(jsonPath("$.createdAt").isNotEmpty()).
                andExpect(jsonPath("$.updatedAt").isNotEmpty()).
                andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void pollingRecoversCompletedStateAfterWebSocketEventWasMissed() throws Exception {
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\"}";
        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted());

        var job = jobRepository.findAll().getFirst();
        job.setStatus(ImportJobStatus.DONE);
        job.setProgress((short) 100);
        job.setCompletedAt(LocalDateTime.now());
        var track = trackRepository.findById(job.getTrack().getId()).orElseThrow();
        track.setAudioObjectKey("tracks/poll/audio.mp3");
        track.setAudioChecksum("a".repeat(64));
        track.setAudioSizeBytes(1234L);
        trackRepository.saveAndFlush(track);
        jobRepository.saveAndFlush(job);
        when(mediaStorage.exists("tracks/poll/audio.mp3")).thenReturn(true);

        mockMvc.perform(get("/api/admin/tracks/import/" + job.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("DONE")).
                andExpect(jsonPath("$.progress").value(100)).
                andExpect(jsonPath("$.previewAvailable").value(true)).
                andExpect(jsonPath("$.completedAt").isNotEmpty()).
                andExpect(jsonPath("$.track.id").value(track.getId().toString())).
                andExpect(jsonPath("$.track.audioObjectKey").doesNotExist());
    }

    @Test
    void pollingExposesOnlyPersistedSafeFailureMessage() throws Exception {
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\"}";
        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted());

        var job = jobRepository.findAll().getFirst();
        job.setStatus(ImportJobStatus.FAILED);
        job.setErrorMessage("The media provider could not process this video");
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.saveAndFlush(job);

        mockMvc.perform(get("/api/admin/tracks/import/" + job.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("FAILED")).
                andExpect(jsonPath("$.errorMessage").value(
                        "The media provider could not process this video")).
                andExpect(jsonPath("$.previewAvailable").value(false)).
                andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void getJobStatusReturns404ForUnknownJob() throws Exception {
        mockMvc.perform(get("/api/admin/tracks/import/" + UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    @Test
    void startImportReturns400ForMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{}")).
                andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedUrlFailsBeforePersistenceOrProcessScheduling() throws Exception {
        String body = """
                {
                  "youtubeUrl": "https://example.com/watch?v=dQw4w9WgXcQ"
                }
                """;

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).
                andExpect(jsonPath("$.fieldErrors.youtubeUrl").exists());

        assertThat(jobRepository.count()).isZero();
        assertThat(trackRepository.count()).isZero();
        verify(importJobScheduler, never()).schedule(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void oversizedUrlIsRejected() throws Exception {
        String body = "{\"youtubeUrl\":\"https://youtu.be/" + "x".repeat(501) + "\"}";

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).
                andExpect(jsonPath("$.fieldErrors.youtubeUrl").exists());
    }

    @Test
    void equivalentUrlCannotCreateDuplicateActiveImport() throws Exception {
        String firstBody = "{\"youtubeUrl\":\"" + WATCH_URL + "\"}";
        String duplicateBody = "{\"youtubeUrl\":\"https://youtu.be/" + VIDEO_ID + "\"}";

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(firstBody)).
                andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(duplicateBody)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("IMPORT_ALREADY_ACTIVE"));

        assertThat(jobRepository.count()).isOne();
        assertThat(trackRepository.count()).isOne();
    }

    @Test
    void uploadRequiresAdminRole() throws Exception {
        mockMvc.perform(multipart("/api/admin/tracks/import/upload").file(audioFile())).
                andExpect(status().isUnauthorized());

        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(audioFile()).
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden());
        verify(audioProbe, never()).probe(any(), any());
    }

    @Test
    void uploadStagesFileAndQueuesJobWithFormMetadataOverTags() throws Exception {
        when(audioProbe.probe(any(), any())).thenReturn(Optional.of(
                new ProbedAudio(Duration.ofMillis(185_900), "Tag title", "Tag artist")));

        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(audioFile()).
                        param("title", "  Form title ").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isAccepted()).
                andExpect(jsonPath("$.jobId").isNotEmpty());

        var job = jobRepository.findAll().getFirst();
        var track = trackRepository.findById(job.getTrack().getId()).orElseThrow();
        assertThat(job.getSource()).isEqualTo(ImportSource.UPLOAD);
        assertThat(job.getYoutubeUrl()).isNull();
        assertThat(job.getOriginalFilename()).isEqualTo("song.mp3");
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.QUEUED);
        assertThat(track.getTitle()).isEqualTo("Form title");
        assertThat(track.getArtist()).isEqualTo("Tag artist");
        assertThat(track.getDurationSeconds()).isEqualTo(185);
        assertThat(track.getYoutubeUrl()).isNull();
        assertThat(uploadStorage.stagedSource(job.getId())).hasBinaryContent(AUDIO_BYTES);
        verify(importJobScheduler).schedule(job.getId());

        mockMvc.perform(get("/api/admin/tracks/import/" + job.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.source").value("UPLOAD")).
                andExpect(jsonPath("$.originalFilename").value("song.mp3"));
        uploadStorage.deleteStagedSource(job.getId());
    }

    @Test
    void unsupportedUploadIsRejectedBeforePersistence() throws Exception {
        when(audioProbe.probe(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(audioFile()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isUnsupportedMediaType()).
                andExpect(jsonPath("$.code").value("IMPORT_FILE_UNSUPPORTED"));

        assertNothingPersistedOrStaged();
    }

    @Test
    void emptyUploadIsRejectedWithoutProbing() throws Exception {
        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(new MockMultipartFile("file", "empty.mp3", "audio/mpeg", new byte[0])).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isUnsupportedMediaType()).
                andExpect(jsonPath("$.code").value("IMPORT_FILE_UNSUPPORTED"));

        verify(audioProbe, never()).probe(any(), any());
        assertNothingPersistedOrStaged();
    }

    @Test
    void uploadWithoutTitleOrArtistAnywhereIsRejected() throws Exception {
        when(audioProbe.probe(any(), any())).thenReturn(Optional.of(
                new ProbedAudio(Duration.ofSeconds(120), "Tag title", null)));

        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(audioFile()).
                        param("artist", "   ").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("IMPORT_METADATA_REQUIRED"));

        assertNothingPersistedOrStaged();
    }

    @Test
    void uploadLongerThanTheDurationLimitIsRejected() throws Exception {
        when(audioProbe.probe(any(), any())).thenReturn(Optional.of(
                new ProbedAudio(Duration.ofMinutes(15).plusSeconds(1), "Title", "Artist")));

        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(audioFile()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("IMPORT_AUDIO_TOO_LONG"));

        assertNothingPersistedOrStaged();
    }

    @Test
    void uploadWithoutFilePartIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        param("title", "Title").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void oversizedUploadMetadataIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(audioFile()).
                        param("title", "x".repeat(256)).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).
                andExpect(jsonPath("$.fieldErrors.title").exists());

        verify(audioProbe, never()).probe(any(), any());
    }

    private static final byte[] AUDIO_BYTES = {'I', 'D', '3', 4, 0, 0};

    private MockMultipartFile audioFile() {
        return new MockMultipartFile("file", "C:\\music\\song.mp3", "audio/mpeg", AUDIO_BYTES);
    }

    private void assertNothingPersistedOrStaged() throws Exception {
        assertThat(jobRepository.count()).isZero();
        assertThat(trackRepository.count()).isZero();
        verify(importJobScheduler, never()).schedule(any());
        Path uploads = uploadStorage.stagedSource(UUID.randomUUID()).getParent();
        if (Files.exists(uploads)) {
            try (var files = Files.list(uploads)) {
                assertThat(files).isEmpty();
            }
        }
    }
}
