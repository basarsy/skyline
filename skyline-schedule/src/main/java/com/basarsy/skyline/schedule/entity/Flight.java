package com.basarsy.skyline.schedule.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "flights")
public class Flight extends BaseEntity {

    @Column(name = "flight_number", nullable = false)
    private String flightNumber;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "aircraft_id", nullable = false)
    private UUID aircraftId;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlightStatus status;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Version
    private Long version;
}
