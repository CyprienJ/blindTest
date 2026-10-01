package com.blindtest.service;

import com.blindtest.model.entity.AdminProvisioningAuditEntity;
import com.blindtest.model.entity.AdminProvisioningOutcome;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.AdminProvisioningAuditRepository;
import com.blindtest.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional first-administrator provisioning boundary. */
@Service
@RequiredArgsConstructor
public class FirstAdminProvisioningService {

    private final UserRepository userRepository;
    private final AdminProvisioningAuditRepository auditRepository;

    /**
     * Promotes the verified account when no administrator exists.
     *
     * <p>The initial locking query serializes provisioning attempts across application
     * instances. Results are audited without persisting the submitted email address.
     */
    @Transactional
    public FirstAdminProvisioningResult provision(String email) {
        userRepository.lockAllUsersForFirstAdminProvisioning();
        Optional<UserEntity> target = userRepository.findByEmailIgnoreCase(email);
        List<UserEntity> administrators = userRepository.findByRole(UserRole.ADMIN);

        if (target.isEmpty()) {
            return audit(AdminProvisioningOutcome.USER_NOT_FOUND, null);
        }

        UserEntity user = target.orElseThrow();
        if (administrators.size() == 1
                && administrators.getFirst().getId().equals(user.getId())) {
            return audit(AdminProvisioningOutcome.ALREADY_ADMIN, user.getId());
        }
        if (!administrators.isEmpty()) {
            return audit(AdminProvisioningOutcome.ADMIN_ALREADY_EXISTS, user.getId());
        }
        if (!user.isEmailVerified()) {
            return audit(AdminProvisioningOutcome.EMAIL_NOT_VERIFIED, user.getId());
        }

        user.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(user);
        return audit(AdminProvisioningOutcome.PROMOTED, user.getId());
    }

    private FirstAdminProvisioningResult audit(
            AdminProvisioningOutcome outcome, UUID targetUserId) {
        auditRepository.save(new AdminProvisioningAuditEntity(outcome, targetUserId));
        return new FirstAdminProvisioningResult(outcome, targetUserId);
    }
}
