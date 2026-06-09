package com.basarsy.skyline.crew.entity;

import com.basarsy.skyline.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "flight_crew")
public class FlightCrew extends BaseEntity {

    @Column(name = "flight_id", nullable = false)
    private UUID flightId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_member_id", nullable = false)
    private CrewMember crewMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrewRole role;
}
