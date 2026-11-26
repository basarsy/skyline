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
     * Counts a wrong password, in one statement so concurrent attempts can't slip past the limit: the
     * {@code maxFailures}-th in a row locks the account until {@code lockUntil} and starts the count again.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.lockedUntil = CASE WHEN u.failedLogins + 1 >= :maxFailures THEN :lockUntil ELSE u.lockedUntil END,
                u.failedLogins = CASE WHEN u.failedLogins + 1 >= :maxFailures THEN 0 ELSE u.failedLogins + 1 END
            WHERE u.id = :id
            """)
    int recordFailedLogin(@Param("id") UUID id, @Param("maxFailures") int maxFailures,
            @Param("lockUntil") LocalDateTime lockUntil);

    /** A successful login: the count starts again. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.failedLogins = 0, u.lockedUntil = null WHERE u.id = :id")
    int clearFailedLogins(@Param("id") UUID id);
}
