package com.blindtest.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Safe persistent audit record for first-administrator provisioning. */
@Entity
@Table(name = "admin_provisioning_audit")
@Getter
@Setter
@NoArgsConstructor
public class AdminProvisioningAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminProvisioningOutcome outcome;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime attemptedAt;

    /** Creates an audit record with no email address or other submitted identifier. */
    public AdminProvisioningAuditEntity(
            AdminProvisioningOutcome outcome, UUID targetUserId) {
        this.outcome = outcome;
        this.targetUserId = targetUserId;
    }

    @PrePersist
    void onCreate() {
        attemptedAt = LocalDateTime.now();
    }
}
