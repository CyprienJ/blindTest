package com.blindtest.service;

import com.blindtest.model.entity.AdminProvisioningOutcome;
import java.util.UUID;

/** Result of one first-administrator provisioning request. */
public record FirstAdminProvisioningResult(
        AdminProvisioningOutcome outcome,
        UUID targetUserId) {
}
