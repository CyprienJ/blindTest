package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.TrackRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import java.util.List;
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
    "spring.datasource.url=jdbc:h2:mem:admintest;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AdminTrackControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private String adminToken;
    private TrackEntity pendingTrack;
    private TrackEntity publishedTrack;

    @BeforeEach
    void setUp() {
        trackRepository.deleteAll();

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.ADMIN);

        TrackEntity pending = buildTrack("Pending Track", TrackStatus.PENDING);
        pendingTrack = trackRepository.save(pending);

        TrackEntity published = buildTrack("Published Track", TrackStatus.PUBLISHED);
        publishedTrack = trackRepository.save(published);
    }

    @Test
    void getAllTracksRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/tracks")).
                andExpect(status().isUnauthorized());

        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        mockMvc.perform(get("/api/admin/tracks").
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden());
    }

    @Test
    void getAllTracksReturnsAllStatuses() throws Exception {
        mockMvc.perform(get("/api/admin/tracks").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(2)).
                andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAllTracksIncludesMediaMetadataButNotObjectKey() throws Exception {
        pendingTrack.setAudioChecksum("a".repeat(64));
        pendingTrack.setAudioSizeBytes(1234L);
        publishedTrack.setAudioChecksum("a".repeat(64));
        publishedTrack.setAudioSizeBytes(1234L);
        trackRepository.saveAllAndFlush(List.of(pendingTrack, publishedTrack));

        mockMvc.perform(get("/api/admin/tracks").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.content[0].audioUrl").isNotEmpty()).
                andExpect(jsonPath("$.content[0].audioChecksum").value("a".repeat(64))).
                andExpect(jsonPath("$.content[0].audioSizeBytes").value(1234)).
                andExpect(jsonPath("$.content[0].audioObjectKey").doesNotExist()).
                andExpect(jsonPath("$.content[0].status").isNotEmpty());
    }

    @Test
    void updateTrackMetadataReturns200WithUpdatedFields() throws Exception {
        String id = pendingTrack.getId().toString();
        mockMvc.perform(put("/api/admin/tracks/" + id).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"title\":\"Updated Title\",\"artist\":\"Updated Artist\"}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.title").value("Updated Title")).
                andExpect(jsonPath("$.artist").value("Updated Artist"));
    }

    @Test
    void updateTrackReturns404ForUnknownId() throws Exception {
        mockMvc.perform(put("/api/admin/tracks/" + UUID.randomUUID()).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"title\":\"New Title\"}")).
                andExpect(status().isNotFound());
    }

    @Test
    void deleteTrackReturns204AndRemovesRecord() throws Exception {
        String id = pendingTrack.getId().toString();
        mockMvc.perform(delete("/api/admin/tracks/" + id).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(trackRepository.findById(pendingTrack.getId())).isEmpty();
    }

    @Test
    void deleteTrackReturns404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/admin/tracks/" + UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    @Test
    void validateTrackSetsStatusToPublished() throws Exception {
        String id = pendingTrack.getId().toString();
        mockMvc.perform(post("/api/admin/tracks/" + id + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void validateTrackReturns404ForUnknownId() throws Exception {
        mockMvc.perform(post("/api/admin/tracks/" + UUID.randomUUID() + "/validate").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    @Test
    void rejectTrackSetsStatusToRejected() throws Exception {
        String id = publishedTrack.getId().toString();
        mockMvc.perform(post("/api/admin/tracks/" + id + "/reject").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private TrackEntity buildTrack(String title, TrackStatus status) {
        TrackEntity track = new TrackEntity();
        track.setTitle(title);
        track.setArtist("Test Artist");
        track.setAudioUrl("https://example.com/audio.mp3");
        track.setStatus(status);
        return track;
    }
}
