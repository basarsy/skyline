package com.basarsy.skyline.reservation.repository;

import com.basarsy.skyline.reservation.entity.AncillaryPrice;
import com.basarsy.skyline.reservation.entity.RouteScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AncillaryPriceRepository extends JpaRepository<AncillaryPrice, UUID> {

    List<AncillaryPrice> findByRouteScope(RouteScope routeScope);

    List<AncillaryPrice> findByProductCode(String productCode);

    Optional<AncillaryPrice> findByProductCodeAndRouteScope(String productCode, RouteScope routeScope);
}
