package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.infra.cover.CoverHttpClient;
import com.blindtest.infra.cover.CoverImageTranscoder;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import com.blindtest.testing.InMemoryMediaStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:covertest;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "app.media-maintenance.deletion-poll-delay-ms=3600000"
})
class AdminCoverControllerIntegrationTest {

    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16,
        'J', 'F', 'I', 'F', 0, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
    private static final String DEEZER_ALBUMS = """
            {"data": [{"title": "Hunting High and Low", "artist": {"name": "a-ha"},
              "cover_medium": "https://cdn-images.dzcdn.net/a/250.jpg",
              "cover_xl": "https://cdn-images.dzcdn.net/a/1000.jpg"}]}
            """;
    private static final String DEEZER_TRACKS = """
            {"data": [{"artist": {"name": "a-ha"}, "album": {"title": "Headlines and Deadlines",
              "cover_medium": "https://cdn-images.dzcdn.net/b/250.jpg",
              "cover_xl": "https://cdn-images.dzcdn.net/b/1000.jpg"}}]}
            """;
    private static final String ITUNES = """
            {"results": [{"collectionName": "Hunting High and Low (Deluxe)", "artistName": "a-ha",
              "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/c/100x100bb.jpg"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MediaDeletionTaskRepository deletionTaskRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private InMemoryMediaStorage storage;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CoverHttpClient coverHttpClient;

    @MockBean
    private CoverImageTranscoder transcoder;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private String adminToken;
    private String userToken;
    private TrackEntity track;

    @TestConfiguration
    static class StorageConfig {

        @Bean
        @Primary
        InMemoryMediaStorage inMemoryMediaStorage() {
            return new InMemoryMediaStorage();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        deletionTaskRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();
        storage.clear();
        adminToken = bearer(user("cover-admin", UserRole.ADMIN));
        userToken = bearer(user("cover-player", UserRole.USER));

        TrackEntity entity = new TrackEntity();
        entity.setTitle("Take On Me");
        entity.setArtist("a-ha");
        entity.setStatus(TrackStatus.PENDING);
        track = trackRepository.save(entity);

        when(coverHttpClient.getJson(any())).thenAnswer(invocation -> catalogue(invocation.getArgument(0)));
        when(coverHttpClient.exists(any())).thenReturn(false);
        when(coverHttpClient.downloadImage(any())).thenReturn(JPEG);
        doAnswer(invocation -> Files.write((Path) invocation.getArgument(2), JPEG)).
                when(transcoder).toJpeg(any(), any(), any());
    }

    @Test
    void suggestionsAreReservedToAdministrators() throws Exception {
        mockMvc.perform(post("/api/admin/cover-suggestions").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"title\": \"Take On Me\", \"artist\": \"a-ha\"}")).
                andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/cover-suggestions").
                        header(HttpHeaders.AUTHORIZATION, userToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"title\": \"Take On Me\", \"artist\": \"a-ha\"}")).
                andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/cover-suggestions").
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"title\": \" \", \"artist\": \"a-ha\"}")).
                andExpect(status().isBadRequest());
    }

    @Test
    void suggestsCoversInProviderOrderAndProxiesTheirThumbnails() throws Exception {
        JsonNode suggestions = suggest();

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions.findValuesAsText("source")).
                containsExactly("DEEZER", "DEEZER", "ITUNES");
        assertThat(suggestions.get(0).get("album").asText()).isEqualTo("Hunting High and Low");
        assertThat(suggestions.toString()).doesNotContain("dzcdn", "mzstatic");

        mockMvc.perform(get("/api/admin/cover-suggestions/{id}/thumbnail",
                        suggestions.get(0).get("candidateId").asText()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(content().contentType(MediaType.IMAGE_JPEG)).
                andExpect(content().bytes(JPEG)).
                andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=1800, private"));
    }

    @Test
    void storesTheChosenCoverServesItPubliclyAndRemovesIt() throws Exception {
        String candidateId = suggest().get(0).get("candidateId").asText();

        String body = mockMvc.perform(put("/api/admin/tracks/{id}", track.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"coverCandidateId\": \"" + candidateId + "\"}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.coverSource").value("DEEZER")).
                andReturn().getResponse().getContentAsString();
        String coverUrl = objectMapper.readTree(body).get("coverUrl").asText();
        assertThat(coverUrl).matches("/api/covers/[0-9a-f-]{36}");
        UUID coverId = UUID.fromString(coverUrl.substring(coverUrl.lastIndexOf('/') + 1));
        assertThat(storage.content("covers/" + coverId + ".jpg")).isEqualTo(JPEG);

        mockMvc.perform(get(coverUrl)).
                andExpect(status().isOk()).
                andExpect(content().contentType(MediaType.IMAGE_JPEG)).
                andExpect(content().bytes(JPEG)).
                andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"));

        mockMvc.perform(put("/api/admin/tracks/{id}", track.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"removeCover\": true}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.coverUrl").doesNotExist());
        assertThat(deletionTaskRepository.findByObjectKey("covers/" + coverId + ".jpg")).isPresent();
    }

    @Test
    void uploadsAnImageAsACandidateAndRejectsOtherFiles() throws Exception {
        String body = mockMvc.perform(multipart("/api/admin/cover-suggestions/upload").
                        file(new MockMultipartFile("file", "cover.png", "image/png", PNG)).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.source").value("UPLOAD")).
                andReturn().getResponse().getContentAsString();
        String candidateId = objectMapper.readTree(body).get("candidateId").asText();

        mockMvc.perform(put("/api/admin/tracks/{id}", track.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"coverCandidateId\": \"" + candidateId + "\"}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.coverSource").value("UPLOAD"));

        mockMvc.perform(multipart("/api/admin/cover-suggestions/upload").
                        file(new MockMultipartFile("file", "cover.jpg", "image/jpeg",
                                "<svg onload=alert(1)>".getBytes())).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isUnsupportedMediaType()).
                andExpect(jsonPath("$.code").value("COVER_IMAGE_UNSUPPORTED"));
    }

    @Test
    void rejectsUnknownCandidatesAndCovers() throws Exception {
        mockMvc.perform(put("/api/admin/tracks/{id}", track.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"coverCandidateId\": \"" + UUID.randomUUID() + "\"}")).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("COVER_SUGGESTION_EXPIRED"));
        assertThat(trackRepository.findById(track.getId()).orElseThrow().getCoverId()).isNull();

        mockMvc.perform(get("/api/covers/{id}", UUID.randomUUID())).
                andExpect(status().isNotFound());
    }

    private JsonNode suggest() throws Exception {
        String body = mockMvc.perform(post("/api/admin/cover-suggestions").
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("""
                                {"title": "Take On Me", "artist": "a-ha", "album": "Hunting High and Low"}
                                """)).
                andExpect(status().isOk()).
                andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode catalogue(URI uri) throws IOException {
        return switch (uri.getHost()) {
            case "api.deezer.com" -> objectMapper.readTree(
                    uri.getPath().contains("album") ? DEEZER_ALBUMS : DEEZER_TRACKS);
            case "itunes.apple.com" -> objectMapper.readTree(
                    uri.getQuery().contains("entity=album") ? ITUNES : "{\"results\": []}");
            default -> throw new IOException("MusicBrainz unavailable");
        };
    }

    private UserEntity user(String username, UserRole role) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(role);
        return userRepository.save(user);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
    }
}
