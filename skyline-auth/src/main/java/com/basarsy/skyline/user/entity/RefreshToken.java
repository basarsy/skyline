package com.basarsy.skyline.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An issued refresh token, keyed by its {@code jti}. All of a login's tokens share a {@code familyId}.
 * {@code replacedAt} is set when the token was rotated (refreshed), {@code revokedAt} whenever it stopped
 * being usable (rotation, logout or reuse detection).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_at")
    private LocalDateTime replacedAt;

    public RefreshToken(UUID id, UUID userId, UUID familyId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}
