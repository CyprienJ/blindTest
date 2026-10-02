package com.blindtest.service;

import com.blindtest.model.entity.UserRole;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.InvitationIssuer.IssuedInvitation;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the bootstrap invitation that lets the future first administrator create an account
 * while registration is invitation-only and no administrator can send invitations yet.
 */
@Service
@RequiredArgsConstructor
public class FirstAdminInvitationService {

    private final UserRepository userRepository;
    private final InvitationIssuer invitationIssuer;

    /**
     * Returns a registration link for {@code email}, or empty when an administrator already
     * exists or the address already has an account. Replaces any open invitation for it.
     */
    @Transactional
    public Optional<IssuedInvitation> invite(String email) {
        userRepository.lockAllUsersForFirstAdminProvisioning();
        if (!userRepository.findByRole(UserRole.ADMIN).isEmpty()
                || userRepository.existsByEmailIgnoreCase(InvitationIssuer.normalizeEmail(email))) {
            return Optional.empty();
        }
        return Optional.of(invitationIssuer.issue(email, null));
    }
}
