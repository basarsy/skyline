package com.basarsy.skyline.route.repository;

import com.basarsy.skyline.route.entity.Route;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, UUID> {
}
