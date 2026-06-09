package com.basarsy.skyline.fleet.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "aircraft_types")
public class AircraftType extends BaseEntity {

    @Column(nullable = false)
    private String manufacturer;

    @Column(nullable = false)
    private String model;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "cabin_config")
    private String cabinConfig;
}
