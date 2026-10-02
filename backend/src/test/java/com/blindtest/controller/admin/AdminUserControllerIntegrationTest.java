package com.blindtest.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.RefreshTokenEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.InvitationRepository;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.RefreshTokenRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:adminusers;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AdminUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EmailSender emailSender;

    @MockBean
    private TurnstileVerifier turnstileVerifier;

    private UserEntity admin;
    private UserEntity player;
    private String adminToken;

    @BeforeEach
    void setUp() {
        playlistRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        invitationRepository.deleteAll();
        userRepository.deleteAll();
        admin = saveUser("admin", UserRole.ADMIN);
        player = saveUser("player", UserRole.USER);
        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin.getId(), UserRole.ADMIN);
    }

    @Test
    void userEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());

        String userToken = "Bearer " + jwtTokenProvider.generateAccessToken(player.getId(), UserRole.USER);
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/{id}", admin.getId()).
                        header(HttpHeaders.AUTHORIZATION, userToken)).
                andExpect(status().isForbidden());
        assertThat(userRepository.findById(admin.getId())).isPresent();
    }

    @Test
    void listsEveryAccountWithItsCounts() throws Exception {
        PlaylistEntity playlist = new PlaylistEntity();
        playlist.setName("Admin playlist");
        playlist.setCreatedBy(admin);
        playlistRepository.save(playlist);

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isOk()).
                andExpect(jsonPath("$.length()").value(2)).
                andExpect(jsonPath("$[?(@.username == 'admin')].playlistCount").value(1)).
                andExpect(jsonPath("$[?(@.username == 'player')].role").value("USER")).
                andExpect(jsonPath("$[?(@.username == 'player')].gamesPlayed").value(0)).
                andExpect(jsonPath("$[?(@.username == 'player')].emailVerified").value(true)).
                andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void editsAnAccountAndSignsItOutWhenTheRoleChanges() throws Exception {
        saveRefreshToken(player);

        update(player.getId(), "renamed", "Renamed@Example.com", "ADMIN").
                andExpect(status().isOk()).
                andExpect(jsonPath("$.username").value("renamed")).
                andExpect(jsonPath("$.email").value("renamed@example.com")).
                andExpect(jsonPath("$.role").value("ADMIN"));

        UserEntity saved = userRepository.findById(player.getId()).orElseThrow();
        assertThat(saved.getUsername()).isEqualTo("renamed");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void keepsSessionsWhenOnlyProfileFieldsChange() throws Exception {
        saveRefreshToken(player);

        update(player.getId(), "renamed", "player@example.com", "USER").andExpect(status().isOk());

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void rejectsDuplicateUsernameAndEmail() throws Exception {
        update(player.getId(), "admin", "player@example.com", "USER").
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        update(player.getId(), "player", "ADMIN@example.com", "USER").
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        update(player.getId(), "player", "not-an-email", "USER").
                andExpect(status().isBadRequest());
    }

    @Test
    void administratorCannotChangeOwnRoleOrDeleteThemselves() throws Exception {
        update(admin.getId(), "admin", "admin@example.com", "USER").
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("ADMIN_SELF_CHANGE_FORBIDDEN"));
        update(admin.getId(), "admin-renamed", "admin@example.com", "ADMIN").
                andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/users/{id}", admin.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("ADMIN_SELF_CHANGE_FORBIDDEN"));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void deletesAnAccountAndItsSessions() throws Exception {
        saveRefreshToken(player);

        mockMvc.perform(delete("/api/admin/users/{id}", player.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNoContent());

        assertThat(userRepository.findById(player.getId())).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
        mockMvc.perform(delete("/api/admin/users/{id}", player.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isNotFound());
    }

    @Test
    void refusesToDeleteAnAccountThatOwnsPlaylists() throws Exception {
        UserEntity otherAdmin = saveUser("curator", UserRole.ADMIN);
        PlaylistEntity playlist = new PlaylistEntity();
        playlist.setName("Curated");
        playlist.setCreatedBy(otherAdmin);
        playlistRepository.save(playlist);

        mockMvc.perform(delete("/api/admin/users/{id}", otherAdmin.getId()).
                        header(HttpHeaders.AUTHORIZATION, adminToken)).
                andExpect(status().isConflict()).
                andExpect(jsonPath("$.code").value("USER_OWNS_PLAYLISTS"));
        assertThat(playlistRepository.findAll()).hasSize(1);
    }

    private ResultActions update(UUID id, String username, String email, String role) throws Exception {
        return mockMvc.perform(put("/api/admin/users/{id}", id).
                header(HttpHeaders.AUTHORIZATION, adminToken).
                contentType(MediaType.APPLICATION_JSON).
                content("{\"username\":\"" + username + "\",\"email\":\"" + email
                        + "\",\"role\":\"" + role + "\"}"));
    }

    private UserEntity saveUser(String username, UserRole role) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(true);
        user.setRole(role);
        return userRepository.save(user);
    }

    private void saveRefreshToken(UserEntity user) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshTokenRepository.save(token);
    }
}
