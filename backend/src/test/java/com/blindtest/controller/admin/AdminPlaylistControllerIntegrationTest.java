package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.EmailService;
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
    "spring.datasource.url=jdbc:h2:mem:adminplaylist;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AdminPlaylistControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EmailService emailService;

    @MockBean
    private TurnstileService turnstileService;

    private String adminToken;
    private UserEntity admin;
    private PlaylistEntity playlist;
    private TrackEntity track;

    @BeforeEach
    void setUp() {
        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        admin = new UserEntity();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);

        track = new TrackEntity();
        track.setTitle("Test Track");
        track.setArtist("Test Artist");
        track.setAudioUrl("/media/test.mp3");
        track.setStatus(TrackStatus.PUBLISHED);
        track = trackRepository.save(track);

        playlist = new PlaylistEntity();
        playlist.setName("Test Playlist");
        playlist.setCreatedBy(admin);
        playlist = playlistRepository.save(playlist);
    }

    @Test
    void getAllPlaylistsRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/playlists")).
                andExpect(status().isUnauthorized());

        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        mockMvc.perform(get("/api/admin/playlists").
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden());
    }

    @Test
    void getAllPlaylistsReturnsAllPlaylists() throws Exception {
        mockMvc.perform(get("/api/admin/playlists").
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$").isArray()).
                andExpect(jsonPath("$[0].name").value("Test Playlist")).
                andExpect(jsonPath("$[0].trackCount").value(0));
    }

    @Test
    void createPlaylistReturns201WithPlaylist() throws Exception {
        String body = "{\"name\":\"New Playlist\",\"description\":\"A cool playlist\"}";
        mockMvc.perform(post("/api/admin/playlists").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.name").value("New Playlist")).
                andExpect(jsonPath("$.trackCount").value(0));
    }

    @Test
    void createPlaylistReturns400ForMissingName() throws Exception {
        mockMvc.perform(post("/api/admin/playlists").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{}")).
                andExpect(status().isBadRequest());
    }

    @Test
    void getPlaylistDetailReturnsPlaylistWithTracks() throws Exception {
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, 1));

        mockMvc.perform(get("/api/admin/playlists/" + playlist.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.name").value("Test Playlist")).
                andExpect(jsonPath("$.trackCount").value(1)).
                andExpect(jsonPath("$.tracks[0].title").value("Test Track"));
    }

    @Test
    void getPlaylistReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/admin/playlists/" + UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    @Test
    void updatePlaylistReturns200WithUpdatedFields() throws Exception {
        String body = "{\"name\":\"Updated Name\"}";
        mockMvc.perform(put("/api/admin/playlists/" + playlist.getId()).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void updatePlaylistReturns404ForUnknownId() throws Exception {
        String body = "{\"name\":\"Updated Name\"}";
        mockMvc.perform(put("/api/admin/playlists/" + UUID.randomUUID()).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isNotFound());
    }

    @Test
    void deletePlaylistReturns204AndRemovesPlaylist() throws Exception {
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, 1));

        mockMvc.perform(delete("/api/admin/playlists/" + playlist.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(playlistRepository.findById(playlist.getId())).isEmpty();
        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).isEmpty();
    }

    @Test
    void addTrackReturns200WithUpdatedPlaylist() throws Exception {
        String body = "{\"trackId\":\"" + track.getId() + "\",\"order\":1}";
        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.trackCount").value(1)).
                andExpect(jsonPath("$.tracks[0].title").value("Test Track"));
    }

    @Test
    void addTrackReturns404ForUnknownTrackId() throws Exception {
        String body = "{\"trackId\":\"" + UUID.randomUUID() + "\",\"order\":1}";
        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isNotFound());
    }

    @Test
    void removeTrackReturns204() throws Exception {
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, 1));

        mockMvc.perform(delete("/api/admin/playlists/" + playlist.getId() + "/tracks/" + track.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).isEmpty();
    }

    @Test
    void removeTrackReturns404ForUnknownTrack() throws Exception {
        mockMvc.perform(delete("/api/admin/playlists/" + playlist.getId() + "/tracks/" + UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }
}
