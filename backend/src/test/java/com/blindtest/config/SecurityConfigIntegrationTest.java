package com.blindtest.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.UserRole;
import com.blindtest.security.JwtAuthFilter;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.AuthService;
import com.blindtest.service.CatalogueService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
    "jwt.secret=test-secret-key-that-is-32-bytes!!",
    "jwt.email-verification.ttl-ms=86400000"
})
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthService authService;

    @MockBean
    private CatalogueService catalogueService;

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
    void publicGetRoutesAreAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/tracks")).andExpect(status().isOk());
        mockMvc.perform(get("/api/playlists/123")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/sessions/ABCDEF")).andExpect(status().isNotFound());
        mockMvc.perform(get("/media/audio.mp3")).andExpect(status().isNotFound());
    }

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRouteWithInvalidTokenReturnsUnauthorized() throws Exception {
        var req = get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, "Bearer bad.token");
        mockMvc.perform(req).andExpect(status().isUnauthorized());
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
        mockMvc.perform(req).andExpect(status().isForbidden());
    }

    @Test
    void adminRouteWithAdminTokenReturnsNotFound() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.ADMIN);
        var req = get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        mockMvc.perform(req).andExpect(status().isNotFound());
    }
}
