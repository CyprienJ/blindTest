package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.AdminProvisioningOutcome;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.AdminProvisioningAuditRepository;
import com.blindtest.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(FirstAdminProvisioningService.class)
class FirstAdminProvisioningServiceTest {

    @Autowired
    private FirstAdminProvisioningService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AdminProvisioningAuditRepository auditRepository;

    @Test
    void promotesVerifiedUserAndAuditsOnlySafeIdentifiers() {
        UserEntity user = saveUser("candidate", true);

        FirstAdminProvisioningResult result = service.provision(user.getEmail());

        assertThat(result.outcome()).isEqualTo(AdminProvisioningOutcome.PROMOTED);
        assertThat(result.targetUserId()).isEqualTo(user.getId());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole()).
                isEqualTo(UserRole.ADMIN);
        assertThat(auditRepository.findAll()).singleElement().satisfies(audit -> {
            assertThat(audit.getOutcome()).isEqualTo(AdminProvisioningOutcome.PROMOTED);
            assertThat(audit.getTargetUserId()).isEqualTo(user.getId());
            assertThat(audit.getAttemptedAt()).isNotNull();
        });
    }

    @Test
    void repeatedProvisioningOfSameUserIsIdempotent() {
        UserEntity user = saveUser("candidate", true);

        FirstAdminProvisioningResult first = service.provision(user.getEmail());
        FirstAdminProvisioningResult second = service.provision(user.getEmail());

        assertThat(first.outcome()).isEqualTo(AdminProvisioningOutcome.PROMOTED);
        assertThat(second.outcome()).isEqualTo(AdminProvisioningOutcome.ALREADY_ADMIN);
        assertThat(userRepository.findByRole(UserRole.ADMIN)).extracting(UserEntity::getId).
                containsExactly(user.getId());
        assertThat(auditRepository.findAll()).extracting(audit -> audit.getOutcome()).
                containsExactly(
                        AdminProvisioningOutcome.PROMOTED,
                        AdminProvisioningOutcome.ALREADY_ADMIN);
    }

    @Test
    void refusesUnverifiedOrUnknownAccountAndAuditsEachAttempt() {
        UserEntity unverified = saveUser("unverified", false);

        FirstAdminProvisioningResult unverifiedResult =
                service.provision(unverified.getEmail());
        FirstAdminProvisioningResult unknownResult =
                service.provision("missing@example.com");

        assertThat(unverifiedResult.outcome()).
                isEqualTo(AdminProvisioningOutcome.EMAIL_NOT_VERIFIED);
        assertThat(unknownResult.outcome()).
                isEqualTo(AdminProvisioningOutcome.USER_NOT_FOUND);
        assertThat(userRepository.findByRole(UserRole.ADMIN)).isEmpty();
        assertThat(auditRepository.findAll()).
                extracting(audit -> audit.getOutcome(), audit -> audit.getTargetUserId()).
                containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                AdminProvisioningOutcome.EMAIL_NOT_VERIFIED,
                                unverified.getId()),
                        org.assertj.core.groups.Tuple.tuple(
                                AdminProvisioningOutcome.USER_NOT_FOUND, null));
    }

    @Test
    void refusesDifferentAccountAfterAdministratorExists() {
        UserEntity first = saveUser("first", true);
        UserEntity second = saveUser("second", true);
        service.provision(first.getEmail());

        FirstAdminProvisioningResult result = service.provision(second.getEmail());

        assertThat(result.outcome()).
                isEqualTo(AdminProvisioningOutcome.ADMIN_ALREADY_EXISTS);
        assertThat(userRepository.findByRole(UserRole.ADMIN)).extracting(UserEntity::getId).
                containsExactly(first.getId());
        assertThat(userRepository.findById(second.getId()).orElseThrow().getRole()).
                isEqualTo(UserRole.USER);
    }

    private UserEntity saveUser(String username, boolean verified) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(verified);
        return userRepository.saveAndFlush(user);
    }
}
