package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.InvitationRepository;
import com.blindtest.repository.RefreshTokenRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String ALICE_PASSWORD = "password123";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        invitationRepository.deleteAll();
        userRepository.deleteAll();
        createUser("alice", ALICE_EMAIL, ALICE_PASSWORD, true);
        createUser("bob", "bob@example.com", "password123", false);
    }

    @Test
    void loginWithValidCredentialsReturns200WithAccessTokenAndCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + ALICE_EMAIL + "\",\"password\":\"" + ALICE_PASSWORD + "\"}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.accessToken").isNotEmpty()).
                andExpect(jsonPath("$.user.email").value(ALICE_EMAIL)).
                andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refreshToken=").contains("HttpOnly").contains("Path=/api/auth");
    }

    @Test
    void loginIgnoresEmailCase() throws Exception {
        mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + ALICE_EMAIL.toUpperCase(Locale.ROOT)
                                + "\",\"password\":\"" + ALICE_PASSWORD + "\"}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.user.email").value(ALICE_EMAIL));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + ALICE_EMAIL + "\",\"password\":\"wrongpass\"}")).
                andExpect(status().isUnauthorized()).
                andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).
                andExpect(jsonPath("$.message").value("Identifiants invalides")).
                andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void invalidLoginRequestReturnsValidationDetails() throws Exception {
        mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"invalid\",\"password\":\"\"}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).
                andExpect(jsonPath("$.fieldErrors.email").isNotEmpty()).
                andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    void registeringWithoutInvitationIsRejected() throws Exception {
        when(turnstileVerifier.verify(any())).thenReturn(true);

        mockMvc.perform(post("/api/auth/register").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"username\":\"charlie\",\"email\":\"charlie@example.com\""
                                + ",\"password\":\"password123\",\"turnstileToken\":\"valid\"}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).
                andExpect(jsonPath("$.fieldErrors.inviteToken").isNotEmpty());

        assertThat(userRepository.findByEmailIgnoreCase("charlie@example.com")).isEmpty();
    }

    @Test
    void loginWithUnverifiedEmailReturns403() throws Exception {
        mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"bob@example.com\",\"password\":\"password123\"}")).
                andExpect(status().isForbidden());
    }

    @Test
    void loginWithUnknownEmailReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"nobody@example.com\",\"password\":\"password123\"}")).
                andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithValidCookieReturns200WithNewAccessToken() throws Exception {
        String refreshToken = loginAndGetRefreshToken();

        mockMvc.perform(post("/api/auth/refresh").
                        cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken))).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refreshWithNoCookieReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).
                andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithInvalidCookieReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").
                        cookie(new Cookie(REFRESH_COOKIE_NAME, "not-a-real-token"))).
                andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesToken() throws Exception {
        String firstToken = loginAndGetRefreshToken();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").
                        cookie(new Cookie(REFRESH_COOKIE_NAME, firstToken))).
                andExpect(status().isOk()).
                andReturn();

        String newCookie = refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(newCookie).contains("refreshToken=");
        String newToken = extractRefreshTokenValue(newCookie);
        assertThat(newToken).isNotEqualTo(firstToken);

        mockMvc.perform(post("/api/auth/refresh").
                        cookie(new Cookie(REFRESH_COOKIE_NAME, firstToken))).
                andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshWithSameCookieRotatesOnceAndRejectsTheOthers() throws Exception {
        int concurrentRequests = 8;
        for (int round = 0; round < 10; round++) {
            String refreshToken = loginAndGetRefreshToken();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            try {
                List<Future<Integer>> statuses = new ArrayList<>();
                for (int i = 0; i < concurrentRequests; i++) {
                    statuses.add(executor.submit(() -> {
                        start.await();
                        return mockMvc.perform(post("/api/auth/refresh").
                                        cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken))).
                                andReturn().getResponse().getStatus();
                    }));
                }
                start.countDown();

                List<Integer> results = new ArrayList<>();
                for (Future<Integer> status : statuses) {
                    results.add(status.get(30, TimeUnit.SECONDS));
                }
                assertThat(results).containsOnly(200, 401);
                assertThat(results).filteredOn(s -> s == 200).hasSize(1);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void logoutWithValidCookieReturns200AndClearsCookie() throws Exception {
        String refreshToken = loginAndGetRefreshToken();

        MvcResult result = mockMvc.perform(post("/api/auth/logout").
                        cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken))).
                andExpect(status().isOk()).
                andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("Max-Age=0");

        mockMvc.perform(post("/api/auth/refresh").
                        cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken))).
                andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithNoCookieReturns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).
                andExpect(status().isOk());
    }

    @Test
    void forgotPasswordWithKnownEmailReturns200AndSendsEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + ALICE_EMAIL + "\"}")).
                andExpect(status().isOk());

        verify(emailSender).sendPasswordResetEmail(eq(ALICE_EMAIL), any(), any());
    }

    @Test
    void forgotPasswordWithUnknownEmailReturns200AndSendsNoEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"ghost@example.com\"}")).
                andExpect(status().isOk());

        verify(emailSender, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void resetPasswordWithValidTokenChangesPasswordAndAllowsLogin() throws Exception {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);

        mockMvc.perform(post("/api/auth/forgot-password").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + ALICE_EMAIL + "\"}")).
                andExpect(status().isOk());

        verify(emailSender).sendPasswordResetEmail(eq(ALICE_EMAIL), any(), tokenCaptor.capture());
        String resetToken = tokenCaptor.getValue();

        mockMvc.perform(post("/api/auth/reset-password").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"newpassword99\"}")).
                andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + ALICE_EMAIL + "\",\"password\":\"newpassword99\"}")).
                andExpect(status().isOk());
    }

    @Test
    void resetPasswordWithInvalidTokenReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"token\":\"not.a.valid.jwt\",\"newPassword\":\"newpassword99\"}")).
                andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordWithEmailVerificationTokenReturns400() throws Exception {
        UserEntity user = userRepository.findByEmailIgnoreCase(ALICE_EMAIL).orElseThrow();
        String wrongPurposeToken = jwtTokenProvider.generateEmailVerificationToken(user.getId());

        mockMvc.perform(post("/api/auth/reset-password").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"token\":\"" + wrongPurposeToken + "\",\"newPassword\":\"newpassword99\"}")).
                andExpect(status().isBadRequest());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private UserEntity createUser(String username, String email, String password, boolean verified) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(verified);
        return userRepository.save(user);
    }

    private String loginAndGetRefreshToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + ALICE_EMAIL + "\",\"password\":\"" + ALICE_PASSWORD + "\"}")).
                andExpect(status().isOk()).
                andReturn();
        return extractRefreshTokenValue(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
    }

    private String extractRefreshTokenValue(String setCookieHeader) {
        if (setCookieHeader == null) {
            return null;
        }
        for (String part : setCookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("refreshToken=")) {
                return trimmed.substring("refreshToken=".length());
            }
        }
        return null;
    }
}
