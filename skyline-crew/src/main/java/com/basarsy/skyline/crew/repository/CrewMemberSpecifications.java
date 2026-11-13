package com.basarsy.skyline.crew.repository;

import com.basarsy.skyline.crew.entity.CrewMember;
import com.basarsy.skyline.crew.entity.CrewRole;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filters of the crew directory; each applies only when set. */
public final class CrewMemberSpecifications {

    private static final char ESCAPE = '\\';

    private CrewMemberSpecifications() {
    }

    /**
     * @param role home role, or null for any
     * @param q    case-insensitive "contains" match on first name, last name or employee number, or null
     */
    public static Specification<CrewMember> matching(CrewRole role, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern, ESCAPE),
                        cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE),
                        cb.like(cb.lower(root.get("employeeNumber")), pattern, ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        String escape = String.valueOf(ESCAPE);
        return value.replace(escape, escape + escape).replace("%", escape + "%").replace("_", escape + "_");
    }
}
