package com.basarsy.skyline.checkin.mapper;

import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.entity.BoardingPass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BoardingPassMapper {

    @Mapping(target = "pnr", source = "reservation.pnr")
    @Mapping(target = "passengerName", expression = "java(boardingPass.getReservation().getPassenger().getFirstName() + \" \" + boardingPass.getReservation().getPassenger().getLastName())")
    @Mapping(target = "flightNumber", source = "reservation.flight.flightNumber")
    @Mapping(target = "origin", source = "reservation.flight.route.origin.iataCode")
    @Mapping(target = "destination", source = "reservation.flight.route.destination.iataCode")
    @Mapping(target = "departureTime", source = "reservation.flight.departureTime")
    BoardingPassResponse toResponse(BoardingPass boardingPass);
}
