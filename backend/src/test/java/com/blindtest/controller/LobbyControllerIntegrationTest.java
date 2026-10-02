package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
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
import com.blindtest.model.game.GameRandom;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackAnswerAliasRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import com.blindtest.store.SessionStore;
import com.blindtest.websocket.SessionEventPublisher;
import com.blindtest.websocket.protocol.PublicSessionEvent;
import com.blindtest.websocket.protocol.PublicSessionEventPayload;
import com.blindtest.websocket.protocol.PublicSessionEventType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** MP-02 acceptance: lobbies, join codes, readiness, host start, and stable failures. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:lobbycontroller;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class LobbyControllerIntegrationTest {

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
    private SessionEventPublisher eventPublisher;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private UserEntity host;
    private UserEntity guest;
    private PlaylistEntity playlist;

    @BeforeEach
    void setUp() {
        aliasRepository.deleteAll();
        playlistTrackRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        userRepository.deleteAll();

        host = saveUser("host", true);
        guest = saveUser("guest", true);
        playlist = new PlaylistEntity();
        playlist.setName("Party");
        playlist.setCreatedBy(host);
        playlist = playlistRepository.save(playlist);
        for (int index = 0; index < 6; index++) {
            TrackEntity track = new TrackEntity();
            track.setTitle("Title " + index);
            track.setArtist("Artist " + index);
            track.setAudioObjectKey("tracks/" + index + "/audio.mp3");
            track.setAudioChecksum("checksum-" + index);
            track.setAudioSizeBytes(1000L + index);
            track.setDurationSeconds(180);
            track.setExcerptStartSeconds(0);
            track.setStatus(TrackStatus.PUBLISHED);
            track = trackRepository.save(track);
            playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, index));
        }
    }

    @Test
    void twoPlayersJoinByCodeBecomeReadyAndTheHostStartsTheGame() throws Exception {
        JsonNode created = json(perform(post("/api/lobbies"), host, configuration()).
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern(
                        "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"))).
                andExpect(jsonPath("$.participants.length()").value(1)).
                andExpect(jsonPath("$.participants[0].host").value(true)).
                andExpect(jsonPath("$.canStart").value(false)).
                andExpect(jsonPath("$.maxParticipants").value(10)).
                andExpect(jsonPath("$.playlistName").value("Party")).
                andExpect(jsonPath("$.trackCount").value(5)).
                andExpect(jsonPath("$.maxRoundSeconds").value(30)));
        String code = created.get("code").asText();
        UUID sessionId = UUID.fromString(created.get("sessionId").asText());

        // Codes are case-insensitive for people typing them in.
        JsonNode joined = json(perform(
                post("/api/lobbies/" + code.toLowerCase() + "/participants"), guest, null).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.participants.length()").value(2)).
                andExpect(jsonPath("$.participants[1].displayName").value("guest")).
                andExpect(jsonPath("$.participants[1].ready").value(false)));
        String guestParticipantId = joined.get("participantId").asText();
        // Joining twice is idempotent.
        perform(post("/api/lobbies/" + code + "/participants"), guest, null).
                andExpect(jsonPath("$.participantId").value(guestParticipantId)).
                andExpect(jsonPath("$.participants.length()").value(2));

        perform(post("/api/lobbies/" + code + "/start"), host, null).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("LOBBY_NOT_READY"));
        perform(put("/api/lobbies/" + code + "/participants/me/ready"), guest, "{\"ready\":true}").
                andExpect(jsonPath("$.canStart").value(true));
        perform(post("/api/lobbies/" + code + "/start"), guest, null).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("LOBBY_HOST_REQUIRED"));

        perform(post("/api/lobbies/" + code + "/start"), host, null).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.sessionId").value(sessionId.toString())).
                andExpect(jsonPath("$.state").value("CREATED")).
                andExpect(jsonPath("$.participantId").value(created.get("participantId").asText()));

        ActiveSessionSnapshot session =
                sessionStore.find(new SessionId(sessionId), clock.instant()).orElseThrow();
        assertThat(session.gameState().status()).isEqualTo(GameStatus.CREATED);
        assertThat(session.gameState().trackSelection().tracks()).hasSize(5);
        assertThat(session.participants()).extracting(SessionParticipant::accountId).
                containsExactly(Optional.of(host.getId()), Optional.of(guest.getId()));
        assertThat(session.ownerParticipantId().value().toString()).
                isEqualTo(created.get("participantId").asText());

        // The code is spent once the game starts.
        perform(post("/api/lobbies/" + code + "/participants"), saveUser("late", true), null).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("LOBBY_NOT_FOUND"));

        ArgumentCaptor<PublicSessionEvent> events = ArgumentCaptor.forClass(PublicSessionEvent.class);
        verify(eventPublisher, atLeastOnce()).publishPublic(events.capture());
        List<PublicSessionEventType> types =
                events.getAllValues().stream().map(PublicSessionEvent::type).toList();
        assertThat(types).containsExactly(
                PublicSessionEventType.LOBBY_UPDATED,
                PublicSessionEventType.LOBBY_UPDATED,
                PublicSessionEventType.LOBBY_STARTED);
        PublicSessionEventPayload.LobbyUpdated ready =
                (PublicSessionEventPayload.LobbyUpdated) events.getAllValues().get(1).payload();
        assertThat(ready.canStart()).isTrue();
        assertThat(events.getAllValues()).allSatisfy(event ->
                assertThat(event.metadata().sessionId()).isEqualTo(sessionId));
    }

    @Test
    void eleventhPlayerIsRejectedWithAStableCode() throws Exception {
        String code = createLobby();
        for (int index = 1; index < 10; index++) {
            perform(post("/api/lobbies/" + code + "/participants"), saveUser("p" + index, true), null).
                    andExpect(status().isOk());
        }

        perform(post("/api/lobbies/" + code + "/participants"), saveUser("p10", true), null).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("LOBBY_FULL"));
    }

    @Test
    void unknownMalformedAndLeftLobbiesLookTheSame() throws Exception {
        perform(post("/api/lobbies/ZZZZZZ/participants"), guest, null).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("LOBBY_NOT_FOUND"));
        perform(post("/api/lobbies/not-a-code/participants"), guest, null).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("LOBBY_NOT_FOUND"));

        String code = createLobby();
        // A registered non-member cannot read the lobby.
        perform(get("/api/lobbies/" + code), guest, null).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("LOBBY_NOT_FOUND"));
        // The last member leaving closes the lobby and frees the code.
        perform(delete("/api/lobbies/" + code + "/participants/me"), host, null).
                andExpect(status().isNoContent());
        perform(post("/api/lobbies/" + code + "/participants"), guest, null).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("LOBBY_NOT_FOUND"));
    }

    @Test
    void hostKicksAndLeavingHostHandsOverTheRole() throws Exception {
        String code = createLobby();
        UserEntity third = saveUser("third", true);
        String guestId = json(perform(post("/api/lobbies/" + code + "/participants"), guest, null)).
                get("participantId").asText();
        perform(post("/api/lobbies/" + code + "/participants"), third, null);

        perform(delete("/api/lobbies/" + code + "/participants/" + guestId), third, null).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("LOBBY_HOST_REQUIRED"));
        perform(delete("/api/lobbies/" + code + "/participants/" + guestId), host, null).
                andExpect(status().isNoContent());
        perform(get("/api/lobbies/" + code), guest, null).
                andExpect(status().isNotFound());

        perform(delete("/api/lobbies/" + code + "/participants/me"), host, null).
                andExpect(status().isNoContent());
        perform(get("/api/lobbies/" + code), third, null).
                andExpect(jsonPath("$.participants.length()").value(1)).
                andExpect(jsonPath("$.participants[0].host").value(true)).
                andExpect(jsonPath("$.hostParticipantId").value(
                        jsonPathValue(perform(get("/api/lobbies/" + code), third, null), "participantId")));
    }

    @Test
    void creationValidatesTheConfigurationAndRequiresAVerifiedAccount() throws Exception {
        perform(post("/api/lobbies"), saveUser("unverified", false), configuration()).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        perform(post("/api/lobbies"), host,
                "{\"playlistId\":\"" + playlist.getId() + "\",\"trackCount\":10,"
                        + "\"maxRoundSeconds\":30,\"extensions\":{}}").
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("GAME_PLAYLIST_INSUFFICIENT_TRACKS"));
        mockMvc.perform(post("/api/lobbies").
                        contentType(MediaType.APPLICATION_JSON).
                        content(configuration())).
                andExpect(status().isUnauthorized());
    }

    private String createLobby() throws Exception {
        return json(perform(post("/api/lobbies"), host, configuration()).
                andExpect(status().isCreated())).get("code").asText();
    }

    private ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            UserEntity user,
            String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), UserRole.USER));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String jsonPathValue(ResultActions result, String field) throws Exception {
        return json(result).get(field).asText();
    }

    private String configuration() {
        return "{\"playlistId\":\"" + playlist.getId() + "\",\"trackCount\":5,"
                + "\"maxRoundSeconds\":30,\"extensions\":{}}";
    }

    private UserEntity saveUser(String username, boolean verified) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(verified);
        return userRepository.save(user);
    }
}
