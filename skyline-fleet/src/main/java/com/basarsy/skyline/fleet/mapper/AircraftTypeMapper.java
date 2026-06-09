package com.basarsy.skyline.fleet.mapper;

import com.basarsy.skyline.fleet.dto.AircraftTypeRequest;
import com.basarsy.skyline.fleet.dto.AircraftTypeResponse;
import com.basarsy.skyline.fleet.dto.AircraftTypeSummary;
import com.basarsy.skyline.fleet.entity.AircraftType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AircraftTypeMapper {

    AircraftTypeResponse toResponse(AircraftType aircraftType);

    AircraftTypeSummary toSummary(AircraftType aircraftType);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AircraftType toEntity(AircraftTypeRequest request);
}
