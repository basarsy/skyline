package com.basarsy.skyline.reservation.mapper;

import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.Reservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {PassengerMapper.class})
public interface ReservationMapper {

    @Mapping(target = "flight", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "ancillaries", ignore = true)
    ReservationResponse toResponse(Reservation reservation);
}
