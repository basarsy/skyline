package com.basarsy.skyline.user.repository;

import com.basarsy.skyline.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Claims one login attempt before its password is checked: counted unless the account is locked at
     * {@code now}, and the {@code maxAttempts}-th in a row locks it until {@code lockUntil} (the count starts
     * again). One statement, so however many attempts arrive at once, at most {@code maxAttempts} passwords are
     * checked per lock. Returns 0 when the account is locked (no attempt allowed), 1 otherwise.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.lockedUntil = CASE WHEN u.failedLogins + 1 >= :maxAttempts THEN :lockUntil ELSE u.lockedUntil END,
                u.failedLogins = CASE WHEN u.failedLogins + 1 >= :maxAttempts THEN 0 ELSE u.failedLogins + 1 END,
                u.updatedAt = :now
            WHERE u.id = :id AND (u.lockedUntil IS NULL OR u.lockedUntil <= :now)
            """)
    int claimLoginAttempt(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts,
            @Param("now") LocalDateTime now, @Param("lockUntil") LocalDateTime lockUntil);

    /** A successful login: the attempts it claimed are given back, and any lock that ended is cleared. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.failedLogins = 0, u.lockedUntil = null, u.updatedAt = :now WHERE u.id = :id")
    int clearFailedLogins(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
