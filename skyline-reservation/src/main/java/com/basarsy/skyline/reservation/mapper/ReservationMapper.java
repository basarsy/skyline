package com.basarsy.skyline.reservation.mapper;

import com.basarsy.skyline.reservation.dto.ReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {PassengerMapper.class})
public interface ReservationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passenger", ignore = true)
    @Mapping(target = "flightId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "bookedAt", ignore = true)
    @Mapping(target = "pnr", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Reservation toEntity(ReservationRequest request);

    @Mapping(target = "flight", ignore = true)
    ReservationResponse toResponse(Reservation reservation);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passenger", ignore = true)
    @Mapping(target = "flightId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "bookedAt", ignore = true)
    @Mapping(target = "pnr", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget Reservation reservation, ReservationRequest request);
}
