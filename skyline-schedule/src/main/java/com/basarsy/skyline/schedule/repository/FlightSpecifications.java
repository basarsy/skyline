package com.basarsy.skyline.schedule.repository;

import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.entity.Flight;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** JPA specifications for the operations flight board; each filter applies only when it is set. */
public final class FlightSpecifications {

    private FlightSpecifications() {
    }

    public static Specification<Flight> matching(FlightBoardFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.date() != null) {
                // Departure times are UTC, and so is the requested day.
                predicates.add(cb.greaterThanOrEqualTo(root.get("departureTime"), filter.date().atStartOfDay()));
                predicates.add(cb.lessThan(root.get("departureTime"), filter.date().plusDays(1).atStartOfDay()));
            }
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.flightNumber() != null && !filter.flightNumber().isBlank()) {
                String pattern = "%" + escapeLike(filter.flightNumber().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("flightNumber")), pattern, '\\'));
            }
            if (filter.routeId() != null) {
                predicates.add(cb.equal(root.get("routeId"), filter.routeId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
