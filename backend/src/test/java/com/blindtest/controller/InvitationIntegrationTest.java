package com.blindtest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.InvitationEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.InvitationRepository;
import com.blindtest.repository.RefreshTokenRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.InvitationIssuer;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:invitations;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class InvitationIntegrationTest {

    private static final String INVITEE = "invitee@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private String adminToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        invitationRepository.deleteAll();
        userRepository.deleteAll();
        when(turnstileVerifier.verify(any())).thenReturn(true);

        UserEntity admin = new UserEntity();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.setEmailVerified(true);
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);
        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);
    }

    @Test
    void invitationEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/invitations")).
                andExpect(status().isUnauthorized());

        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);
        mockMvc.perform(post("/api/admin/invitations").
                        header(HttpHeaders.AUTHORIZATION, userToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + INVITEE + "\"}")).
                andExpect(status().isForbidden());
        verify(emailSender, never()).sendInvitationEmail(anyString(), anyString(), anyString(), any());
    }

    @Test
    void adminInvitationEmailsATwoDayLinkAndStoresOnlyItsHash() throws Exception {
        mockMvc.perform(post("/api/admin/invitations").
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"Invitee@Example.com\"}")).
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.email").value(INVITEE)).
                andExpect(jsonPath("$.status").value("PENDING")).
                andExpect(jsonPath("$.invitedBy").value("admin")).
                andExpect(jsonPath("$.token").doesNotExist());

        String token = sentToken();
        InvitationEntity stored = invitationRepository.findAll().getFirst();
        assertThat(stored.getTokenHash()).isEqualTo(InvitationIssuer.hashToken(token)).isNotEqualTo(token);
        assertThat(Duration.between(stored.getCreatedAt(), stored.getExpiresAt())).
                isBetween(Duration.ofHours(47), Duration.ofHours(49));

        mockMvc.perform(get("/api/admin/invitations").header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$[0].email").value(INVITEE));
    }

    @Test
    void invitingAnExistingAccountReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/admin/invitations").
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"ADMIN@example.com\"}")).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void lookupReturnsTheInvitedAddress() throws Exception {
        String token = invite();

        mockMvc.perform(post("/api/auth/invitations/lookup").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"token\":\"" + token + "\"}")).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.email").value(INVITEE)).
                andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mockMvc.perform(post("/api/auth/invitations/lookup").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"token\":\"unknown\"}")).
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
    }

    @Test
    void registrationCreatesAVerifiedUserForTheInvitedAddressOnlyOnce() throws Exception {
        String token = invite();

        register(token, "invitee").
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.email").value(INVITEE));

        UserEntity created = userRepository.findByEmailIgnoreCase(INVITEE).orElseThrow();
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.getRole()).isEqualTo(UserRole.USER);
        InvitationEntity invitation = invitationRepository.findAll().getFirst();
        assertThat(invitation.getAcceptedAt()).isNotNull();
        assertThat(invitation.getAcceptedUserId()).isEqualTo(created.getId());
        verify(emailSender).sendWelcomeEmail(INVITEE, "invitee");
        verify(emailSender, never()).sendVerificationEmail(anyString(), anyString(), anyString());

        register(token, "someone-else").
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("INVITATION_INVALID"));

        mockMvc.perform(post("/api/auth/login").
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + INVITEE + "\",\"password\":\"password123\"}")).
                andExpect(status().isOk());
    }

    @Test
    void failedRegistrationLeavesTheInvitationUsable() throws Exception {
        String token = invite();

        register(token, "admin").
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        register(token, "invitee").andExpect(status().isCreated());
    }

    @Test
    void expiredInvitationCannotBeUsed() throws Exception {
        String token = invite();
        InvitationEntity invitation = invitationRepository.findAll().getFirst();
        invitation.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        invitationRepository.save(invitation);

        register(token, "invitee").
                andExpect(status().isBadRequest()).
                andExpect(jsonPath("$.code").value("INVITATION_INVALID"));
        mockMvc.perform(get("/api/admin/invitations").header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(jsonPath("$[0].status").value("EXPIRED"));
    }

    @Test
    void revokedInvitationCannotBeUsed() throws Exception {
        String token = invite();
        UUID id = invitationRepository.findAll().getFirst().getId();

        mockMvc.perform(delete("/api/admin/invitations/{id}", id).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        register(token, "invitee").andExpect(status().isBadRequest());
        assertThat(userRepository.findByEmailIgnoreCase(INVITEE)).isEmpty();
    }

    @Test
    void resendingIssuesANewLinkAndInvalidatesThePreviousOne() throws Exception {
        String firstToken = invite();
        UUID firstId = invitationRepository.findAll().getFirst().getId();
        clearInvocations(emailSender);

        mockMvc.perform(post("/api/admin/invitations/{id}/resend", firstId).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isCreated()).
                andExpect(jsonPath("$.status").value("PENDING"));
        String secondToken = sentToken();

        register(firstToken, "invitee").andExpect(status().isBadRequest());
        register(secondToken, "invitee").andExpect(status().isCreated());
        assertThat(invitationRepository.findById(firstId).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void acceptedInvitationCannotBeRevokedOrResent() throws Exception {
        String token = invite();
        register(token, "invitee").andExpect(status().isCreated());
        UUID id = invitationRepository.findAll().getFirst().getId();

        mockMvc.perform(delete("/api/admin/invitations/{id}", id).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("INVITATION_ALREADY_ACCEPTED"));
        mockMvc.perform(post("/api/admin/invitations/{id}/resend", id).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict());
    }

    private String invite() throws Exception {
        mockMvc.perform(post("/api/admin/invitations").
                        header(HttpHeaders.AUTHORIZATION, adminToken).
                        contentType(MediaType.APPLICATION_JSON).
                        content("{\"email\":\"" + INVITEE + "\"}")).
                andExpect(status().isCreated());
        return sentToken();
    }

    private String sentToken() {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendInvitationEmail(
                eq(INVITEE), eq("admin"), token.capture(), eq(Duration.ofDays(2)));
        return token.getValue();
    }

    private org.springframework.test.web.servlet.ResultActions register(String token, String username)
            throws Exception {
        JsonNode body = objectMapper.createObjectNode().
                put("username", username).
                put("inviteToken", token).
                put("password", "password123").
                put("turnstileToken", "valid");
        return mockMvc.perform(post("/api/auth/register").
                contentType(MediaType.APPLICATION_JSON).
                content(objectMapper.writeValueAsString(body)));
    }
}
