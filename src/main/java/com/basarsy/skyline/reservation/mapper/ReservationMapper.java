package com.basarsy.skyline.reservation.mapper;

import com.basarsy.skyline.reservation.dto.ReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.schedule.mapper.FlightMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {PassengerMapper.class, FlightMapper.class})
public interface ReservationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passenger", ignore = true)
    @Mapping(target = "flight", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "seatNumber", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "bookedAt", ignore = true)
    @Mapping(target = "pnr", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Reservation toEntity(ReservationRequest request);

    ReservationResponse toResponse(Reservation reservation);
}
