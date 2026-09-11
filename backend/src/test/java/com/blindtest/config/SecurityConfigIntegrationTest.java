package com.blindtest.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.error.GlobalExceptionHandler;
import com.blindtest.error.RestSecurityErrorHandler;
import com.blindtest.model.entity.UserRole;
import com.blindtest.security.JwtAuthFilter;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.AdminPlaylistService;
import com.blindtest.service.AdminTrackService;
import com.blindtest.service.AuthService;
import com.blindtest.service.CatalogueService;
import com.blindtest.service.GameHistorySummaryService;
import com.blindtest.service.ImportJobService;
import com.blindtest.service.MediaReconciliationService;
import com.blindtest.service.SessionGameplayService;
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
    private CatalogueService catalogueService;

    @MockBean
    private ImportJobService importJobService;

    @MockBean
    private MediaReconciliationService mediaReconciliationService;

    @MockBean
    private SoloSessionService soloSessionService;

    @MockBean
    private GameHistorySummaryService gameHistorySummaryService;

    @MockBean
    private SessionGameplayService sessionGameplayService;

    @Test
    void completedSessionSummaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/sessions/{sessionId}/summary", UUID.randomUUID())).
                andExpect(status().isUnauthorized()).
                andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void publicAuthPostRoutesAreAccessibleWithoutToken() throws Exception {
        // Routes without a controller yet return 404; implemented endpoints return 400
        // (missing/invalid body). Neither should return 401 (security-blocked).
        mockMvc.perform(post("/api/auth/login")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register")).andExpect(status().isBadRequest());
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
        var req = get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        mockMvc.perform(req).andExpect(status().isNotFound());
    }
}
