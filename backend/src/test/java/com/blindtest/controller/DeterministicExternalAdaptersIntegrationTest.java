package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.ImportSource;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.testing.DeterministicTestAdaptersConfig;
import com.blindtest.testing.FakeEmailSender;
import com.blindtest.testing.FakeEmailSender.EmailType;
import com.blindtest.testing.FakeProcessFactory;
import com.blindtest.testing.FakeTurnstileVerifier;
import com.blindtest.testing.InMemoryMediaStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DeterministicTestAdaptersConfig.class)
class DeterministicExternalAdaptersIntegrationTest {

    private static final Path IMPORT_TEMP_DIR = createImportTempDirectory();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private TrackImportJobRepository jobRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private FakeEmailSender emailSender;

    @Autowired
    private FakeTurnstileVerifier turnstileVerifier;

    @Autowired
    private FakeProcessFactory processFactory;

    @Autowired
    private InMemoryMediaStorage mediaStorage;

    @DynamicPropertySource
    static void deterministicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:deterministicadapters;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.import.temp-dir", IMPORT_TEMP_DIR::toString);
        registry.add("app.import.max-size-bytes", () -> 2048);
    }

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();
        emailSender.reset();
        turnstileVerifier.reset();
        processFactory.reset();
        mediaStorage.clear();
    }

    @Test
    void registrationUsesFakeTurnstileAndCapturesVerificationEmail() throws Exception {
        turnstileVerifier.accept("accepted-test-token");

        mockMvc.perform(post("/api/auth/register").
                        contentType(MediaType.APPLICATION_JSON).
                        content("""
                                {
                                  "username": "listener",
                                  "email": "listener@example.com",
                                  "password": "password123",
                                  "turnstileToken": "accepted-test-token",
                                  "role": "ADMIN"
                                }
                                """)).
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.username").value("listener")).
                andExpect(jsonPath("$.email").value("listener@example.com"));

        UserEntity saved = userRepository.findByEmailIgnoreCase("listener@example.com").orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(emailSender.messages()).hasSize(1);
        FakeEmailSender.SentEmail email = emailSender.messages().getFirst();
        assertThat(email.type()).isEqualTo(EmailType.VERIFICATION);
        assertThat(email.toEmail()).isEqualTo("listener@example.com");
        assertThat(jwtTokenProvider.validateEmailVerificationToken(email.token())).isTrue();
        assertThat(jwtTokenProvider.extractUserIdFromEmailVerificationToken(email.token())).
                isEqualTo(saved.getId());
    }

    @Test
    void registrationRejectsATokenNotAcceptedByFakeTurnstile() throws Exception {
        mockMvc.perform(post("/api/auth/register").
                        contentType(MediaType.APPLICATION_JSON).
                        content("""
                                {
                                  "username": "bot",
                                  "email": "bot@example.com",
                                  "password": "password123",
                                  "turnstileToken": "rejected-test-token"
                                }
                                """)).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("TURNSTILE_FAILED"));

        assertThat(userRepository.findByEmailIgnoreCase("bot@example.com")).isEmpty();
        assertThat(emailSender.messages()).isEmpty();
    }

    @Test
    void importCompletesWithFakeProcessAndValidMp3WithoutYoutube() throws Exception {
        UserEntity admin = new UserEntity();
        admin.setUsername("import-admin");
        admin.setEmail("import-admin@example.com");
        admin.setPasswordHash("not-used");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);
        String token = jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);

        String response = mockMvc.perform(post("/api/admin/tracks/import").
                        header(HttpHeaders.AUTHORIZATION, "Bearer " + token).
                        contentType(MediaType.APPLICATION_JSON).
                        content("""
                                {
                                  "youtubeUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                                  "title": "Fixture Song",
                                  "artist": "Fixture Artist"
                                }
                                """)).
                andExpect(status().isAccepted()).
                andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        UUID jobId = UUID.fromString(json.get("jobId").asText());
        var job = jobRepository.findByIdWithTrack(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(job.getProgress()).isEqualTo((short) 100);
        assertThat(job.getTrack().getAudioUrl()).isNull();
        assertThat(job.getTrack().getAudioObjectKey()).
                startsWith("tracks/" + job.getTrack().getId() + "/audio-").
                endsWith(".mp3");
        assertThat(job.getTrack().getAudioChecksum()).hasSize(64);
        assertThat(job.getTrack().getAudioSizeBytes()).isEqualTo(1040L);
        assertThat(mediaStorage.exists(job.getTrack().getAudioObjectKey())).isTrue();
        assertThat(mediaStorage.contentType(job.getTrack().getAudioObjectKey())).isEqualTo("audio/mpeg");
        assertThat(processFactory.startCount()).isEqualTo(1);

        byte[] bytes = mediaStorage.content(job.getTrack().getAudioObjectKey());
        assertThat(bytes).hasSize(1040);
        assertThat(bytes[0] & 0xff).isEqualTo(0xff);
        assertThat(bytes[1] & 0xe0).isEqualTo(0xe0);
        assertImportWorkspaceHoldsNoFiles();
    }

    @Test
    void uploadCompletesThroughProbeAndTranscodeWithEmbeddedTagMetadata() throws Exception {
        UserEntity admin = new UserEntity();
        admin.setUsername("upload-admin");
        admin.setEmail("upload-admin@example.com");
        admin.setPasswordHash("not-used");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);
        String token = jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);

        String response = mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(new MockMultipartFile("file", "fixture.mp3", "audio/mpeg", fixtureMp3())).
                        header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).
                andExpect(status().isAccepted()).
                andReturn().getResponse().getContentAsString();

        UUID jobId = UUID.fromString(objectMapper.readTree(response).get("jobId").asText());
        var job = jobRepository.findByIdWithTrack(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(job.getSource()).isEqualTo(ImportSource.UPLOAD);
        assertThat(job.getTrack().getTitle()).isEqualTo("Deterministic upload title");
        assertThat(job.getTrack().getArtist()).isEqualTo("Deterministic upload artist");
        assertThat(job.getTrack().getDurationSeconds()).isEqualTo(120);
        assertThat(mediaStorage.exists(job.getTrack().getAudioObjectKey())).isTrue();
        assertThat(processFactory.startCount()).isZero();
        assertImportWorkspaceHoldsNoFiles();
    }

    @Test
    void uploadThatIsNotAudioIsRejectedByTheProbe() throws Exception {
        UserEntity admin = new UserEntity();
        admin.setUsername("reject-admin");
        admin.setEmail("reject-admin@example.com");
        admin.setPasswordHash("not-used");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);
        String token = jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);

        mockMvc.perform(multipart("/api/admin/tracks/import/upload").
                        file(new MockMultipartFile("file", "notes.mp3", "audio/mpeg",
                                "plain text".getBytes(StandardCharsets.UTF_8))).
                        header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).
                andExpect(status().isUnsupportedMediaType()).
                andExpect(jsonPath("$.code").value("IMPORT_FILE_UNSUPPORTED"));

        assertThat(jobRepository.count()).isZero();
        assertImportWorkspaceHoldsNoFiles();
    }

    private void assertImportWorkspaceHoldsNoFiles() throws IOException {
        try (var temporaryFiles = Files.walk(IMPORT_TEMP_DIR)) {
            assertThat(temporaryFiles.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private byte[] fixtureMp3() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/silence.mp3.base64")) {
            assertThat(input).isNotNull();
            return Base64.getMimeDecoder().decode(input.readAllBytes());
        }
    }

    private static Path createImportTempDirectory() {
        try {
            return Files.createTempDirectory("blindtest-test-imports-");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create deterministic import directory", exception);
        }
    }
}
