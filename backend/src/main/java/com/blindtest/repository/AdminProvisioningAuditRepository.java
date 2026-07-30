package com.blindtest.repository;

import com.blindtest.model.entity.AdminProvisioningAuditEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for safe first-administrator provisioning audit records. */
public interface AdminProvisioningAuditRepository
        extends JpaRepository<AdminProvisioningAuditEntity, UUID> {
}
