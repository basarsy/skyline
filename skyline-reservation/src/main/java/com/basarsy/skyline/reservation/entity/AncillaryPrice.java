package com.basarsy.skyline.reservation.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A product's price on domestic or international routes, online and at the airport desk. */
@Getter
@Setter
@Entity
@Table(name = "ancillary_prices")
public class AncillaryPrice extends BaseEntity {

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_scope", nullable = false)
    private RouteScope routeScope;

    @Column(name = "online_price", nullable = false)
    private BigDecimal onlinePrice;

    @Column(name = "airport_price", nullable = false)
    private BigDecimal airportPrice;
}
