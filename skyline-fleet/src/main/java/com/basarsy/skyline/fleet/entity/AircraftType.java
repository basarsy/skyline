package com.basarsy.skyline.fleet.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import com.basarsy.skyline.fleet.layout.CabinLayout;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** The seat map; null for a type nobody has drawn yet (its flights then get no seat selection). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cabin_layout", columnDefinition = "jsonb")
    private CabinLayout cabinLayout;
}
