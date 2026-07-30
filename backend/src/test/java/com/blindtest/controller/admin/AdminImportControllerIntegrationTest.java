package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.ImportJobScheduler;
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
                andExpect(jsonPath("$.jobId").value(jobId));
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
