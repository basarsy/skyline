package com.basarsy.skyline.user.repository;

import com.basarsy.skyline.user.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Marks an active token as rotated. A single conditional update, so of two concurrent refreshes with the same
     * token exactly one wins (returns 1) and the other sees it already replaced.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.replacedAt = :now WHERE t.id = :id AND t.revokedAt IS NULL")
    int markReplaced(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
