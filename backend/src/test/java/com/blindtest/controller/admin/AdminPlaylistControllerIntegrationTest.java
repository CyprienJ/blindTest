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
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

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
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, 0));

        mockMvc.perform(get("/api/admin/playlists/" + playlist.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.name").value("Test Playlist")).
                andExpect(jsonPath("$.trackCount").value(1)).
                andExpect(jsonPath("$.tracks[0].title").value("Test Track"));
    }

    @Test
    void getPlaylistDetailExcludesUnavailableTracks() throws Exception {
        TrackEntity pending = track("Pending track", TrackStatus.PENDING);
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, pending, 0));

        mockMvc.perform(get("/api/admin/playlists/" + playlist.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.trackCount").value(0)).
                andExpect(jsonPath("$.tracks").isEmpty());
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
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, 0));

        mockMvc.perform(delete("/api/admin/playlists/" + playlist.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(playlistRepository.findById(playlist.getId())).isEmpty();
        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).isEmpty();
    }

    @Test
    void addTrackReturns200WithUpdatedPlaylist() throws Exception {
        String body = "{\"trackId\":\"" + track.getId() + "\",\"order\":0}";
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
        String body = "{\"trackId\":\"" + UUID.randomUUID() + "\",\"order\":0}";
        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content(body)).
                andExpect(status().isNotFound());
    }

    @Test
    void addTrackInsertsAtRequestedPositionAndPreventsDuplicates() throws Exception {
        TrackEntity second = track("Second track", TrackStatus.PUBLISHED);
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, 0));

        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"trackId\":\"" + second.getId() + "\",\"order\":0}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.tracks[0].id").value(second.getId().toString())).
                andExpect(jsonPath("$.tracks[1].id").value(track.getId().toString()));

        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).
                extracting(PlaylistTrackEntity::getTrackOrder).containsExactly(0, 1);

        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"trackId\":\"" + second.getId() + "\",\"order\":1}")).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("PLAYLIST_TRACK_DUPLICATE"));
    }

    @Test
    void addTrackRejectsUnavailableTrackAndPositionOutsideList() throws Exception {
        TrackEntity pending = track("Pending track", TrackStatus.PENDING);

        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"trackId\":\"" + pending.getId() + "\",\"order\":0}")).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("PLAYLIST_TRACK_NOT_PUBLISHED"));

        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"trackId\":\"" + track.getId() + "\",\"order\":1}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("PLAYLIST_ORDER_INVALID"));

        mockMvc.perform(post("/api/admin/playlists/" + playlist.getId() + "/tracks").
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"trackId\":\"" + track.getId() + "\"}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void reorderRequiresACompleteUniqueSnapshotAndUpdatesAtomically() throws Exception {
        TrackEntity second = track("Second track", TrackStatus.PUBLISHED);
        TrackEntity third = track("Third track", TrackStatus.PUBLISHED);
        playlistTrackRepository.saveAll(List.of(
                new PlaylistTrackEntity(playlist, track, 0),
                new PlaylistTrackEntity(playlist, second, 1),
                new PlaylistTrackEntity(playlist, third, 2)));
        String reorderUrl = "/api/admin/playlists/" + playlist.getId() + "/tracks/order";

        mockMvc.perform(put(reorderUrl).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"trackIds\":[\"" + third.getId() + "\",\""
                                + track.getId() + "\",\"" + second.getId() + "\"]}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.tracks[0].id").value(third.getId().toString())).
                andExpect(jsonPath("$.tracks[1].id").value(track.getId().toString())).
                andExpect(jsonPath("$.tracks[2].id").value(second.getId().toString()));

        mockMvc.perform(put(reorderUrl).
                        contentType(MediaType.APPLICATION_JSON).
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        content("{\"trackIds\":[\"" + track.getId() + "\",\""
                                + track.getId() + "\",\"" + second.getId() + "\"]}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("PLAYLIST_ORDER_INVALID"));

        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).
                extracting(entry -> entry.getTrack().getId()).
                containsExactly(third.getId(), track.getId(), second.getId());
    }

    @Test
    void removeTrackReturns204() throws Exception {
        TrackEntity second = track("Second track", TrackStatus.PUBLISHED);
        playlistTrackRepository.saveAll(List.of(
                new PlaylistTrackEntity(playlist, track, 0),
                new PlaylistTrackEntity(playlist, second, 1)));

        mockMvc.perform(delete("/api/admin/playlists/" + playlist.getId() + "/tracks/" + track.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(playlistTrackRepository.findAllByPlaylistIdOrdered(playlist.getId())).
                singleElement().satisfies(entry -> {
                    assertThat(entry.getTrack().getId()).isEqualTo(second.getId());
                    assertThat(entry.getTrackOrder()).isZero();
                });
    }

    @Test
    void removeTrackReturns404ForUnknownTrack() throws Exception {
        mockMvc.perform(delete("/api/admin/playlists/" + playlist.getId() + "/tracks/" + UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    private TrackEntity track(String title, TrackStatus status) {
        TrackEntity entity = new TrackEntity();
        entity.setTitle(title);
        entity.setArtist("Test Artist");
        entity.setAudioUrl("/media/" + title.toLowerCase().replace(' ', '-') + ".mp3");
        entity.setStatus(status);
        return trackRepository.save(entity);
    }
}
