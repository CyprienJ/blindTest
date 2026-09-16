package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.GameHistoryEntity;
import com.blindtest.model.entity.GameHistoryParticipantEntity;
import com.blindtest.model.entity.GameHistoryRoundEntity;
import com.blindtest.model.entity.HistoryParticipantKind;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.model.game.RoundOutcome;
import com.blindtest.repository.GameHistoryParticipantRepository;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.GameHistoryRoundRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sessionsummary;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class SessionSummaryControllerIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-04T12:00:00Z");
    private static final UUID SESSION_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private GameHistoryRepository historyRepository;
    @Autowired
    private GameHistoryParticipantRepository participantRepository;
    @Autowired
    private GameHistoryRoundRepository roundRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EmailSender emailSender;
    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private UserEntity owner;
    private UserEntity otherUser;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        participantRepository.deleteAll();
        roundRepository.deleteAll();
        userRepository.deleteAll();
        owner = userRepository.save(user("summary-owner"));
        otherUser = userRepository.save(user("summary-other"));
        historyRepository.saveAndFlush(history(owner));
    }

    @Test
    void ownerCanRefreshOrderedDurableSummaryWithSafeAnswerMetadata() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/sessions/{sessionId}/summary", SESSION_ID).
                        header(HttpHeaders.AUTHORIZATION, bearer(owner))).
                andExpect(status().isOk()).
                andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).
                andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString())).
                andExpect(jsonPath("$.totalScore").value(1000)).
                andExpect(jsonPath("$.totalRounds").value(5)).
                andExpect(jsonPath("$.titlesFound").value(1)).
                andExpect(jsonPath("$.skippedRounds").value(3)).
                andExpect(jsonPath("$.timedOutRounds").value(1)).
                andExpect(jsonPath("$.rounds.length()").value(5)).
                andExpect(jsonPath("$.rounds[0].roundIndex").value(0)).
                andExpect(jsonPath("$.rounds[0].outcome").value("TITLE_FOUND")).
                andExpect(jsonPath("$.rounds[0].points").value(1000)).
                andExpect(jsonPath("$.rounds[0].title").value("Title 0")).
                andExpect(jsonPath("$.rounds[0].artist").value("Artist 0")).
                andExpect(jsonPath("$.rounds[0].composer").value("Composer 0")).
                andExpect(jsonPath("$.rounds[0].album").value("Album 0")).
                andExpect(jsonPath("$.rounds[4].roundIndex").value(4)).
                andExpect(jsonPath("$.youtubeUrl").doesNotExist()).
                andExpect(jsonPath("$.audioObjectKey").doesNotExist()).
                andExpect(jsonPath("$.rounds[0].trackId").doesNotExist()).
                andExpect(jsonPath("$.rounds[0].answerAliases").doesNotExist()).
                andExpect(jsonPath("$.rounds[0].youtubeUrl").doesNotExist()).
                andExpect(jsonPath("$.rounds[0].audioObjectKey").doesNotExist()).
                andReturn();

        MvcResult refreshed = mockMvc.perform(
                        get("/api/sessions/{sessionId}/summary", SESSION_ID).
                                header(HttpHeaders.AUTHORIZATION, bearer(owner))).
                andExpect(status().isOk()).
                andReturn();

        assertThat(refreshed.getResponse().getContentAsString()).
                isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void anonymousAndNonOwnerCannotReadSummaryOrDiscoverItsExistence() throws Exception {
        mockMvc.perform(get("/api/sessions/{sessionId}/summary", SESSION_ID)).
                andExpect(status().isUnauthorized()).
                andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/api/sessions/{sessionId}/summary", SESSION_ID).
                        header(HttpHeaders.AUTHORIZATION, bearer(otherUser))).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/sessions/{sessionId}/summary", UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, bearer(owner))).
                andExpect(status().isNotFound()).
                andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void malformedSessionIdentityUsesSafeParameterError() throws Exception {
        mockMvc.perform(get("/api/sessions/not-a-uuid/summary").
                        header(HttpHeaders.AUTHORIZATION, bearer(owner))).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private GameHistoryEntity history(UserEntity historyOwner) {
        GameHistoryEntity history = new GameHistoryEntity();
        history.setSessionId(SESSION_ID);
        history.setHistoryVersion("history-v1");
        history.setGameMode("SOLO");
        history.setProtocolVersion((short) 1);
        history.setConfigurationVersion("solo-v1");
        history.setPlaylistIdSnapshot(UUID.randomUUID());
        history.setRequestedTrackCount(5);
        history.setMaximumRoundDurationSeconds(30);
        history.setConfigurationExtensions("{}");
        history.setTotalScore(1000);
        history.setTotalRounds(5);
        history.setTitlesFound(1);
        history.setSkippedRounds(3);
        history.setTimedOutRounds(1);
        history.setStartedAt(STARTED_AT);
        history.setCompletedAt(STARTED_AT.plus(4, ChronoUnit.MINUTES));
        history.setRetentionExpiresAt(STARTED_AT.plus(730, ChronoUnit.DAYS));
        history.addParticipant(participant(historyOwner));
        for (int index = 4; index >= 0; index--) {
            history.addRound(round(index));
        }
        return history;
    }

    private GameHistoryParticipantEntity participant(UserEntity historyOwner) {
        GameHistoryParticipantEntity participant = new GameHistoryParticipantEntity();
        participant.setParticipantId(UUID.randomUUID());
        participant.setUser(historyOwner);
        participant.setDisplayName(historyOwner.getUsername());
        participant.setParticipantKind(HistoryParticipantKind.AUTHENTICATED);
        participant.setOwner(true);
        participant.setFinalScore(1000);
        participant.setFinalRank(1);
        return participant;
    }

    private GameHistoryRoundEntity round(int index) {
        GameHistoryRoundEntity round = new GameHistoryRoundEntity();
        round.setRoundIndex(index);
        round.setTrackIdSnapshot(UUID.randomUUID());
        round.setTitleSnapshot("Title " + index);
        round.setArtistSnapshot("Artist " + index);
        round.setComposerSnapshot("Composer " + index);
        round.setAlbumSnapshot("Album " + index);
        round.setAnswerAliasesSnapshot("{\"TITLE\":[\"Private alias\"]}");
        round.setAnswerSnapshotVersion("answers-v1");
        round.setExcerptStartSeconds(index);
        round.setOutcome(index == 0 ? RoundOutcome.TITLE_FOUND
                : index == 1 ? RoundOutcome.TIMEOUT : RoundOutcome.SKIPPED);
        round.setPoints(index == 0 ? 1000 : 0);
        round.setScoringRuleVersion("title-linear-v1");
        round.setStartedAt(STARTED_AT.plus(index * 40L, ChronoUnit.SECONDS));
        round.setDeadline(round.getStartedAt().plus(30, ChronoUnit.SECONDS));
        round.setRevealedAt(round.getStartedAt().plus(2, ChronoUnit.SECONDS));
        round.setCompletedAt(round.getRevealedAt().plus(4, ChronoUnit.SECONDS));
        return round;
    }

    private UserEntity user(String username) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(true);
        return user;
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), UserRole.USER);
    }
}
