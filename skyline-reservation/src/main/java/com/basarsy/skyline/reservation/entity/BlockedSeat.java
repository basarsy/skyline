package com.basarsy.skyline.reservation.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A seat taken out of sale on one flight; shown as BLOCKED on the seat map and never assigned. */
@Getter
@Setter
@Entity
@Table(name = "blocked_seats")
public class BlockedSeat extends BaseEntity {

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;

    @Column(name = "seat_number", nullable = false, length = 8)
    private String seatNumber;

    private String reason;
}
