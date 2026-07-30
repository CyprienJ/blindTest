package com.blindtest.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:cattest;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class CatalogueControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private TrackEntity publishedPop;
    private TrackEntity publishedJazz;
    private PlaylistEntity playlist;

    @BeforeEach
    void setUp() {
        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity admin = userRepository.save(buildUser("admin", "admin@example.com"));

        TrackEntity pop = buildTrack("Pop Song", "Pop Artist", "pop", (short) 2000, TrackStatus.PUBLISHED);
        publishedPop = trackRepository.save(pop);

        TrackEntity jazz = buildTrack("Jazz Tune", "Jazz Artist", "jazz", (short) 1990, TrackStatus.PUBLISHED);
        publishedJazz = trackRepository.save(jazz);

        TrackEntity pending = buildTrack("Unpublished", "Unknown", "pop", (short) 2000, TrackStatus.PENDING);
        trackRepository.save(pending);

        PlaylistEntity pl = new PlaylistEntity();
        pl.setName("My Playlist");
        pl.setCreatedBy(admin);
        playlist = playlistRepository.save(pl);

        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, publishedJazz, 1));
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, publishedPop, 2));
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, pending, 3));
    }

    @Test
    void getTracksReturnsOnlyPublishedTracks() throws Exception {
        mockMvc.perform(get("/api/tracks")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(2)).
                andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getTracksFiltersByCategory() throws Exception {
        mockMvc.perform(get("/api/tracks").
                        param("category", "jazz")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(1)).
                andExpect(jsonPath("$.content[0].title").value("Jazz Tune"));
    }

    @Test
    void getTracksFiltersByDecade() throws Exception {
        mockMvc.perform(get("/api/tracks").
                        param("decade", "2000")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.totalElements").value(1)).
                andExpect(jsonPath("$.content[0].title").value("Pop Song"));
    }

    @Test
    void getTracksDoesNotExposeMediaStorageDetailsOrYoutubeUrl() throws Exception {
        mockMvc.perform(get("/api/tracks")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.content[0].audioUrl").doesNotExist()).
                andExpect(jsonPath("$.content[0].audioObjectKey").doesNotExist()).
                andExpect(jsonPath("$.content[0].audioChecksum").doesNotExist()).
                andExpect(jsonPath("$.content[0].audioSizeBytes").doesNotExist()).
                andExpect(jsonPath("$.content[0].youtubeUrl").doesNotExist());
    }

    @Test
    void getPlaylistsReturnsAllPlaylistsWithPublishedTrackCount() throws Exception {
        mockMvc.perform(get("/api/playlists")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$").isArray()).
                andExpect(jsonPath("$[0].name").value("My Playlist")).
                andExpect(jsonPath("$[0].trackCount").value(2));
    }

    @Test
    void getPlaylistDetailReturnsOrderedPublishedTracks() throws Exception {
        String id = playlist.getId().toString();
        mockMvc.perform(get("/api/playlists/" + id)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.name").value("My Playlist")).
                andExpect(jsonPath("$.tracks").isArray()).
                andExpect(jsonPath("$.tracks.length()").value(2)).
                andExpect(jsonPath("$.tracks[0].title").value("Jazz Tune")).
                andExpect(jsonPath("$.tracks[1].title").value("Pop Song"));
    }

    @Test
    void getPlaylistDetailReturns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/playlists/" + UUID.randomUUID())).
                andExpect(status().isNotFound());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private UserEntity buildUser(String username, String email) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private TrackEntity buildTrack(
            String title, String artist, String category, short decade, TrackStatus status) {
        TrackEntity track = new TrackEntity();
        track.setTitle(title);
        track.setArtist(artist);
        track.setAudioUrl("https://example.com/audio.mp3");
        track.setCategory(category);
        track.setDecade(decade);
        track.setStatus(status);
        return track;
    }
}
