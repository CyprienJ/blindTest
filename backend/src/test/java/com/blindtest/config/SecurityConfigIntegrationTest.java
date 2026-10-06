package com.blindtest.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.error.GlobalExceptionHandler;
import com.blindtest.error.RestSecurityErrorHandler;
import com.blindtest.model.entity.UserRole;
import com.blindtest.security.BearerTokenAuthenticator;
import com.blindtest.security.JwtAuthFilter;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.AdminPlaylistService;
import com.blindtest.service.AdminTrackService;
import com.blindtest.service.AdminUserService;
import com.blindtest.service.AuthService;
import com.blindtest.service.CatalogueService;
import com.blindtest.service.GameHistorySummaryService;
import com.blindtest.service.ImportJobService;
import com.blindtest.service.InvitationService;
import com.blindtest.service.MediaReconciliationService;
import com.blindtest.service.RegistrationService;
import com.blindtest.service.CoverSuggestionService;
import com.blindtest.service.SessionGameplayService;
import com.blindtest.service.TrackCoverService;
import com.blindtest.service.LobbyService;
import com.blindtest.service.SoloSessionService;
import com.blindtest.service.TrackAnswerAliasService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@EnableConfigurationProperties(ApplicationProperties.class)
@Import({
    AppConfig.class,
    BearerTokenAuthenticator.class,
    CorrelationIdFilter.class,
    GlobalExceptionHandler.class,
    JwtAuthFilter.class,
    JwtTokenProvider.class,
    RestSecurityErrorHandler.class,
    SecurityConfig.class
})
@TestPropertySource(properties = {
    "jwt.secret=test-secret-key-that-is-32-bytes!!",
    "jwt.email-verification.ttl-ms=86400000",
    "app.base-url=http://localhost:8090"
})
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AdminPlaylistService adminPlaylistService;

    @MockBean
    private AdminTrackService adminTrackService;

    @MockBean
    private TrackAnswerAliasService trackAnswerAliasService;

    @MockBean
    private AuthService authService;

    @MockBean
    private InvitationService invitationService;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private CatalogueService catalogueService;

    @MockBean
    private ImportJobService importJobService;

    @MockBean
    private MediaReconciliationService mediaReconciliationService;

    @MockBean
    private SoloSessionService soloSessionService;

    @MockBean
    private LobbyService lobbyService;

    @MockBean
    private GameHistorySummaryService gameHistorySummaryService;

    @MockBean
    private SessionGameplayService sessionGameplayService;

    @MockBean
    private TrackCoverService trackCoverService;

    @MockBean
    private CoverSuggestionService coverSuggestionService;

    @Test
    void framingIsAllowedOnlyFromSelfAndCjorant() throws Exception {
        mockMvc.perform(get("/api/tracks")).
                andExpect(header().doesNotExist("X-Frame-Options")).
                andExpect(header().string("Content-Security-Policy",
                        "frame-ancestors 'self' https://cjorant.fr"));
    }

    @Test
    void completedSessionSummaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/sessions/{sessionId}/summary", UUID.randomUUID())).
                andExpect(status().isUnauthorized()).
                andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void lobbyRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/lobbies")).
                andExpect(status().isUnauthorized()).
                andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/lobbies/ABCDEF")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lobbies/ABCDEF/participants")).
                andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lobbies/ABCDEF/start")).andExpect(status().isUnauthorized());
    }

    @Test
    void guestJoinIsOpenWithoutAToken() throws Exception {
        mockMvc.perform(post("/api/lobbies/ABCDEF/guests").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"nickname\":\"Visitor\",\"turnstileToken\":\"t\"}")).
                andExpect(status().isOk());
    }

    @Test
    void guestTokensReachOnlyLobbyMemberRoutesAndSummaries() throws Exception {
        String bearer = "Bearer " + jwtTokenProvider.generateGuestToken(
                UUID.randomUUID(), UUID.randomUUID());
        // Lobby member routes and summaries pass security; the services then check membership.
        mockMvc.perform(get("/api/lobbies/ABCDEF").header(HttpHeaders.AUTHORIZATION, bearer)).
                andExpect(status().isOk());
        mockMvc.perform(put("/api/lobbies/ABCDEF/participants/me/ready").
                        header(HttpHeaders.AUTHORIZATION, bearer).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"ready\":true}")).
                andExpect(status().isOk());
        mockMvc.perform(get("/api/sessions/{sessionId}/summary", UUID.randomUUID()).
                        header(HttpHeaders.AUTHORIZATION, bearer)).
                andExpect(status().isOk());
        // Everything that needs an account is forbidden.
        for (var request : java.util.List.of(
                post("/api/lobbies"),
                post("/api/lobbies/ABCDEF/participants"),
                post("/api/sessions"),
                get("/api/users/profile"),
                get("/api/admin/users"))) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer)).
                    andExpect(status().isForbidden());
        }
    }

    @Test
    void publicAuthPostRoutesAreAccessibleWithoutToken() throws Exception {
        // Routes without a controller yet return 404; implemented endpoints return 400
        // (missing/invalid body). Neither should return 401 (security-blocked).
        mockMvc.perform(post("/api/auth/login")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/invitations/lookup")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/verify-email")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/forgot-password")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/reset-password")).andExpect(status().isBadRequest());
    }

    @Test
    void applicationBaseUrlIsAllowedAsCorsOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/register").
                        header(HttpHeaders.ORIGIN, "http://localhost:8090").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{}")).
                andExpect(status().isBadRequest()).
                andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8090"));
    }

    @Test
    void unknownCorsOriginIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register").
                        header(HttpHeaders.ORIGIN, "https://untrusted.example").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{}")).
                andExpect(status().isForbidden()).
                andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void publicGetRoutesAreAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/tracks")).andExpect(status().isOk());
        mockMvc.perform(get("/api/playlists/123")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/sessions/ABCDEF")).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/audio.mp3")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/covers/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/cover-suggestions")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() throws Exception {
        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/users/profile").
                        header(CorrelationIdFilter.HEADER_NAME, requestId)).
                andExpect(status().isUnauthorized()).
                andExpect(header().string(CorrelationIdFilter.HEADER_NAME, requestId)).
                andExpect(jsonPath("$.status").value(401)).
                andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED")).
                andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void protectedRouteWithInvalidTokenReturnsUnauthorized() throws Exception {
        var req = get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer bad.token");
        mockMvc.perform(req).
                andExpect(status().isUnauthorized()).
                andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN")).
                andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void protectedRouteWithValidUserTokenReturnsNotFound() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        var req = get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        mockMvc.perform(req).andExpect(status().isNotFound());
    }

    @Test
    void adminRouteWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRouteWithUserTokenReturnsForbidden() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        var req = get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        mockMvc.perform(req).
                andExpect(status().isForbidden()).
                andExpect(jsonPath("$.code").value("ACCESS_DENIED")).
                andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void adminRouteWithAdminTokenReturnsNotFound() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.ADMIN);
        var req = get("/api/admin/unknown-route").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        mockMvc.perform(req).andExpect(status().isNotFound());
    }
}
