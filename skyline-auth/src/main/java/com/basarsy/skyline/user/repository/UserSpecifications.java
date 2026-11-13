package com.basarsy.skyline.user.repository;

import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the admin user search; each applies only when set. */
public final class UserSpecifications {

    private static final char ESCAPE = '\\';

    private UserSpecifications() {
    }

    public static Specification<User> matching(String email, UserRole role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (email != null && !email.isBlank()) {
                String pattern = "%" + escapeLike(email.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("email")), pattern, ESCAPE));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        String escape = String.valueOf(ESCAPE);
        return value.replace(escape, escape + escape).replace("%", escape + "%").replace("_", escape + "_");
    }
}
