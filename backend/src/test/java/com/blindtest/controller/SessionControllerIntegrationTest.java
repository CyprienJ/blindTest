package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.dto.SoloSessionResponse;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackAnswerAliasEntity;
import com.blindtest.model.entity.TrackAnswerType;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameRandom;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackAnswerAliasRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import com.blindtest.store.SessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sessioncontroller;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class SessionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private PlaylistTrackRepository playlistTrackRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private TrackAnswerAliasRepository aliasRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private Clock clock;

    @MockBean
    private GameRandom gameRandom;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private UserEntity verifiedUser;
    private UserEntity unverifiedUser;
    private PlaylistEntity playlist;
    private List<TrackEntity> tracks;

    @BeforeEach
    void setUp() {
        aliasRepository.deleteAll();
        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        verifiedUser = saveUser("alice", true);
        unverifiedUser = saveUser("bob", false);
        playlist = new PlaylistEntity();
        playlist.setName("Playable selection");
        playlist.setCreatedBy(verifiedUser);
        playlist = playlistRepository.save(playlist);

        tracks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            TrackEntity track = new TrackEntity();
            track.setTitle("Title " + index);
            track.setArtist("Artist " + index);
            track.setComposer("Composer " + index);
            track.setAlbum("Album " + index);
            track.setAudioObjectKey("tracks/" + index + "/audio.mp3");
            track.setAudioChecksum("checksum-" + index);
            track.setAudioSizeBytes(1000L + index);
            track.setDurationSeconds(180);
            track.setExcerptStartSeconds(index);
            track.setStatus(TrackStatus.PUBLISHED);
            tracks.add(trackRepository.save(track));
            playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, index));
        }
        aliasRepository.save(new TrackAnswerAliasEntity(
                tracks.getFirst(), TrackAnswerType.TITLE, "First alias", "first alias"));
    }

    @Test
    void anonymousAndUnverifiedAccountsCannotCreateSessions() throws Exception {
        mockMvc.perform(post("/api/sessions").
                        contentType(MediaType.APPLICATION_JSON).
                        content(validRequest())).
                andExpect(status().isUnauthorized()).
                andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(unverifiedUser)).
                        contentType(MediaType.APPLICATION_JSON).
                        content(validRequest())).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void createsSessionWithFrozenTracksAndConnectionInstructions() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                        contentType(MediaType.APPLICATION_JSON).
                        content(validRequest())).
                andExpect(status().isCreated()).
                andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/api/sessions/"))).
                andExpect(jsonPath("$.state").value("CREATED")).
                andExpect(jsonPath("$.version").value(0)).
                andExpect(jsonPath("$.expiresAt").isNotEmpty()).
                andExpect(jsonPath("$.connection.protocolVersion").value(1)).
                andExpect(jsonPath("$.connection.webSocketEndpoint").value("/ws")).
                andExpect(jsonPath("$.connection.sockJsEndpoint").value("/ws-sockjs")).
                andExpect(jsonPath("$.connection.commandDestination").
                        value(org.hamcrest.Matchers.startsWith("/app/sessions/"))).
                andExpect(jsonPath("$.audioObjectKey").doesNotExist()).
                andReturn();

        SoloSessionResponse response = objectMapper.readValue(
                result.getResponse().getContentAsByteArray(), SoloSessionResponse.class);
        ActiveSessionSnapshot snapshot = sessionStore.find(
                new SessionId(response.sessionId()), clock.instant()).orElseThrow();

        assertThat(snapshot.ownerParticipantId().value()).isEqualTo(response.participantId());
        assertThat(snapshot.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.accountId()).contains(verifiedUser.getId());
            assertThat(participant.displayName()).isEqualTo("alice");
        });
        assertThat(snapshot.gameState().trackSelection().tracks()).hasSize(5);
        assertThat(snapshot.gameState().trackSelection().tracks()).
                extracting(track -> track.trackId()).doesNotHaveDuplicates();
        assertThat(snapshot.gameState().trackSelection().tracks()).
                filteredOn(track -> track.trackId().equals(tracks.getFirst().getId())).
                singleElement().satisfies(track ->
                        assertThat(track.answers().aliases().get(GameAnswerType.TITLE)).
                                containsExactly("First alias"));

        tracks.getFirst().setTitle("Edited after creation");
        trackRepository.saveAndFlush(tracks.getFirst());
        assertThat(snapshot.gameState().trackSelection().tracks()).
                filteredOn(track -> track.trackId().equals(tracks.getFirst().getId())).
                singleElement().satisfies(track ->
                        assertThat(track.answers().title()).isEqualTo("Title 0"));
    }

    @Test
    void sameAccountCanCreateMultipleDistinctSequentialSessions() throws Exception {
        Set<UUID> sessionIds = new HashSet<>();
        Set<UUID> participantIds = new HashSet<>();

        for (int count = 0; count < 3; count++) {
            MvcResult result = mockMvc.perform(post("/api/sessions").
                            header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                            contentType(MediaType.APPLICATION_JSON).
                            content(validRequest())).
                    andExpect(status().isCreated()).
                    andReturn();
            SoloSessionResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsByteArray(), SoloSessionResponse.class);
            sessionIds.add(response.sessionId());
            participantIds.add(response.participantId());
        }

        assertThat(sessionIds).hasSize(3);
        assertThat(participantIds).hasSize(3);
    }

    @Test
    void invalidConfigurationUsesStableDomainErrors() throws Exception {
        mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"trackCount\":5,\"maxRoundSeconds\":30}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("GAME_PLAYLIST_REQUIRED"));

        mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                        contentType(MediaType.APPLICATION_JSON).
                        content(request(playlist.getId(), 4, 30, "{}"))).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("GAME_TRACK_COUNT_INVALID"));

        mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                        contentType(MediaType.APPLICATION_JSON).
                        content(request(playlist.getId(), 5, 30, "{\"mode\":\"team\"}"))).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").
                        value("GAME_CONFIGURATION_EXTENSION_UNSUPPORTED"));

        mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                        contentType(MediaType.APPLICATION_JSON).
                        content(request(UUID.randomUUID(), 5, 30, "{}"))).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("GAME_PLAYLIST_NOT_FOUND"));
    }

    @Test
    void rejectsPlaylistWithoutEnoughSnapshotCapablePublishedMedia() throws Exception {
        tracks.getFirst().setAudioObjectKey(null);
        tracks.getFirst().setAudioChecksum(null);
        tracks.getFirst().setAudioUrl("/media/legacy.mp3");
        trackRepository.saveAndFlush(tracks.getFirst());
        tracks.get(1).setAudioObjectKey(null);
        tracks.get(1).setAudioChecksum(null);
        tracks.get(1).setAudioUrl("/media/legacy-2.mp3");
        trackRepository.saveAndFlush(tracks.get(1));

        mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                        contentType(MediaType.APPLICATION_JSON).
                        content(validRequest())).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").
                        value("GAME_PLAYLIST_INSUFFICIENT_TRACKS"));
    }

    @Test
    void missingBodyReturnsStableConfigurationError() throws Exception {
        mockMvc.perform(post("/api/sessions").
                        header(HttpHeaders.AUTHORIZATION, bearer(verifiedUser)).
                        contentType(MediaType.APPLICATION_JSON)).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("GAME_CONFIGURATION_REQUIRED"));
    }

    private UserEntity saveUser(String username, boolean verified) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(verified);
        return userRepository.save(user);
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), UserRole.USER);
    }

    private String validRequest() {
        return request(playlist.getId(), 5, 30, "{}");
    }

    private String request(UUID playlistId, int trackCount, int duration, String extensions) {
        return "{\"playlistId\":\"" + playlistId + "\",\"trackCount\":" + trackCount
                + ",\"maxRoundSeconds\":" + duration + ",\"extensions\":"
                + extensions + "}";
    }
}
