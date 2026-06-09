package com.basarsy.skyline.checkin.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "boarding_passes")
public class BoardingPass extends BaseEntity {

    @Column(name = "reservation_id", unique = true, nullable = false)
    private UUID reservationId;

    @Column(nullable = false)
    private String gate;

    @Column(name = "boarding_time", nullable = false)
    private LocalDateTime boardingTime;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "barcode_data", nullable = false)
    private String barcodeData;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;
}
