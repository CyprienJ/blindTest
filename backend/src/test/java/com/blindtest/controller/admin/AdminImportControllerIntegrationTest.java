package com.blindtest.controller.admin;

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
import com.blindtest.service.EmailService;
import com.blindtest.service.ImportJobRunner;
import com.blindtest.service.TurnstileService;
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
    private ImportJobRunner importJobRunner;

    @MockBean
    private EmailService emailService;

    @MockBean
    private TurnstileService turnstileService;

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
        String body = "{\"youtubeUrl\":\"https://youtube.com/watch?v=test\","
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
        String body = "{\"youtubeUrl\":\"https://youtube.com/watch?v=test\","
                + "\"title\":\"Song\",\"artist\":\"Artist\"}";

        mockMvc.perform(post("/api/admin/tracks/import").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isAccepted()).
                andExpect(jsonPath("$.jobId").isNotEmpty());
    }

    @Test
    void getJobStatusReturnsQueuedJob() throws Exception {
        String body = "{\"youtubeUrl\":\"https://youtube.com/watch?v=test\","
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
}
