package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.ImportJobScheduler;
import com.blindtest.service.port.MediaStorage;
import java.time.LocalDateTime;
import com.blindtest.service.port.TurnstileVerifier;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\","
                + "\"title\":\"Song\",\"artist\":\"Artist\"}";

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
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\","
                + "\"title\":\"Song\",\"artist\":\"Artist\"}";

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted()).
                andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    void startImportPersistsConsistentOptionalMetadata() throws Exception {
        String body = """
                {
                  "youtubeUrl": "https://youtu.be/dQw4w9WgXcQ",
                  "title": "Film Title",
                  "artist": "Orchestra",
                  "album": "Soundtrack Album",
                  "composer": "Composer",
                  "work": "Main Theme",
                  "category": "film",
                  "decade": 2000,
                  "durationSeconds": 210,
                  "imageUrl": "https://images.example.test/cover.jpg"
                }
                """;

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted());

        var track = trackRepository.findAll().getFirst();
        assertThat(track.getTitle()).isEqualTo("Film Title");
        assertThat(track.getAlbum()).isEqualTo("Soundtrack Album");
        assertThat(track.getComposer()).isEqualTo("Composer");
        assertThat(track.getWork()).isEqualTo("Main Theme");
        assertThat(track.getDurationSeconds()).isEqualTo(210);
        var job = jobRepository.findAll().getFirst();
        assertThat(job.getYoutubeVideoId()).isEqualTo(VIDEO_ID);
        assertThat(job.getYoutubeUrl()).isEqualTo(WATCH_URL);
        assertThat(track.getYoutubeUrl()).isEqualTo(WATCH_URL);
    }

    @Test
    void getJobStatusReturnsQueuedJob() throws Exception {
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\","
                + "\"title\":\"Song\",\"artist\":\"Artist\"}";

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
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\","
                + "\"title\":\"Song\",\"artist\":\"Artist\"}";
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
        String body = "{\"youtubeUrl\":\"" + WATCH_URL + "\","
                + "\"title\":\"Song\",\"artist\":\"Artist\"}";
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
                  "youtubeUrl": "https://example.com/watch?v=dQw4w9WgXcQ",
                  "title": "Song",
                  "artist": "Artist"
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
    void oversizedMetadataIsRejected() throws Exception {
        String body = """
                {
                  "youtubeUrl": "%s",
                  "title": "%s",
                  "artist": "Artist"
                }
                """.formatted(WATCH_URL, "x".repeat(256));

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).
                andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void equivalentUrlCannotCreateDuplicateActiveImport() throws Exception {
        String firstBody = "{\"youtubeUrl\":\"" + WATCH_URL + "\","
                + "\"title\":\"Song\",\"artist\":\"Artist\"}";
        String duplicateBody = "{\"youtubeUrl\":\"https://youtu.be/" + VIDEO_ID + "\","
                + "\"title\":\"Duplicate\",\"artist\":\"Artist\"}";

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
}
