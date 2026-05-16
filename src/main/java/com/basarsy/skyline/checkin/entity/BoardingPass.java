package com.basarsy.skyline.checkin.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import com.basarsy.skyline.reservation.entity.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "boarding_passes")
public class BoardingPass extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

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
