package com.blindtest.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity mapped to the {@code invitations} table. Only a hash of the link token is stored,
 * and every timestamp is UTC.
 */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
public class InvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private UserEntity invitedBy;

    /** UTC creation time, set by the issuer from the application clock. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_user_id")
    private UUID acceptedUserId;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** Returns the lifecycle status of this invitation at {@code now}. */
    public InvitationStatus statusAt(LocalDateTime now) {
        if (acceptedAt != null) {
            return InvitationStatus.ACCEPTED;
        }
        if (revokedAt != null) {
            return InvitationStatus.REVOKED;
        }
        return expiresAt.isAfter(now) ? InvitationStatus.PENDING : InvitationStatus.EXPIRED;
    }
}
