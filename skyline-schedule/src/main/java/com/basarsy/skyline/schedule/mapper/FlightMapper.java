package com.basarsy.skyline.schedule.mapper;

import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.entity.Flight;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface FlightMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "routeId", ignore = true)
    @Mapping(target = "aircraftId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "availableSeats", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Flight toEntity(FlightRequest request);

    @Mapping(target = "route", ignore = true)
    @Mapping(target = "aircraft", ignore = true)
    FlightResponse toResponse(Flight flight);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "routeId", ignore = true)
    @Mapping(target = "aircraftId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "availableSeats", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget Flight flight, FlightRequest request);
}

