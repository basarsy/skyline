package com.basarsy.skyline.user.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    /** Wrong passwords in a row since the last successful login or lockout. */
    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    /** No login succeeds before this (UTC) after too many wrong passwords; null when not locked. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
