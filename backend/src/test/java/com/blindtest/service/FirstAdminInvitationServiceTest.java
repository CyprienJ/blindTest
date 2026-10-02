package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.model.entity.InvitationEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.InvitationRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.InvitationIssuer.IssuedInvitation;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
    FirstAdminInvitationService.class,
    InvitationIssuer.class,
    FirstAdminInvitationServiceTest.Config.class
})
class FirstAdminInvitationServiceTest {

    @Autowired
    private FirstAdminInvitationService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InvitationRepository invitationRepository;

    @Test
    void issuesABootstrapInvitationWhenNoAdministratorExists() {
        IssuedInvitation issued = service.invite("Future@Example.com").orElseThrow();

        assertThat(issued.link()).isEqualTo("https://blindtest.example/auth/register?invite=" + issued.token());
        InvitationEntity stored = invitationRepository.findAll().getFirst();
        assertThat(stored.getEmail()).isEqualTo("future@example.com");
        assertThat(stored.getInvitedBy()).isNull();
        assertThat(stored.getTokenHash()).isEqualTo(InvitationIssuer.hashToken(issued.token()));
    }

    @Test
    void reissuingReplacesTheOpenBootstrapInvitation() {
        service.invite("future@example.com").orElseThrow();
        service.invite("future@example.com").orElseThrow();

        assertThat(invitationRepository.findAll()).
                filteredOn(invitation -> invitation.getRevokedAt() == null).
                hasSize(1);
    }

    @Test
    void refusesOnceAnAdministratorExists() {
        UserEntity admin = new UserEntity();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("hash");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        assertThat(service.invite("future@example.com")).isEmpty();
        assertThat(invitationRepository.findAll()).isEmpty();
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        ApplicationProperties applicationProperties() {
            return new ApplicationProperties(URI.create("https://blindtest.example/"), Path.of("/media"));
        }
    }
}
