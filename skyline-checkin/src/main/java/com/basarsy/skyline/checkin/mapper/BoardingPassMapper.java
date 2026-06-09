package com.basarsy.skyline.checkin.mapper;

import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.entity.BoardingPass;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BoardingPassMapper {

    @Mapping(target = "pnr", ignore = true)
    @Mapping(target = "passengerName", ignore = true)
    @Mapping(target = "flightNumber", ignore = true)
    @Mapping(target = "origin", ignore = true)
    @Mapping(target = "destination", ignore = true)
    @Mapping(target = "departureTime", ignore = true)
    BoardingPassResponse toResponse(BoardingPass boardingPass);
}
