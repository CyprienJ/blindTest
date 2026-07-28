package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.ImportJobStatus;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DeterministicTestAdaptersConfig.class)
class DeterministicExternalAdaptersIntegrationTest {

    private static final Path MEDIA_DIR = createMediaDirectory();

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

    @DynamicPropertySource
    static void deterministicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:deterministicadapters;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.media-dir", MEDIA_DIR::toString);
    }

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();
        emailSender.reset();
        turnstileVerifier.reset();
        processFactory.reset();
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
                                  "turnstileToken": "accepted-test-token"
                                }
                                """)).
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.username").value("listener")).
                andExpect(jsonPath("$.email").value("listener@example.com"));

        UserEntity saved = userRepository.findByEmail("listener@example.com").orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
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

        assertThat(userRepository.findByEmail("bot@example.com")).isEmpty();
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
                                  "youtubeUrl": "https://youtube.example.test/watch?v=fixture",
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
        assertThat(job.getTrack().getAudioUrl()).isEqualTo("/media/" + jobId + ".mp3");
        assertThat(processFactory.startCount()).isEqualTo(1);

        Path mp3 = MEDIA_DIR.resolve(jobId + ".mp3");
        byte[] bytes = Files.readAllBytes(mp3);
        assertThat(bytes).hasSize(1040);
        assertThat(bytes[0] & 0xff).isEqualTo(0xff);
        assertThat(bytes[1] & 0xe0).isEqualTo(0xe0);
    }

    private static Path createMediaDirectory() {
        try {
            return Files.createTempDirectory("blindtest-test-media-");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create deterministic test media directory", exception);
        }
    }
}
