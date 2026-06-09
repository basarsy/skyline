package com.basarsy.skyline.fleet.mapper;

import com.basarsy.skyline.fleet.dto.AircraftResponse;
import com.basarsy.skyline.fleet.entity.Aircraft;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AircraftTypeMapper.class)
public interface AircraftMapper {

    @Mapping(source = "aircraftType", target = "aircraftType")
    AircraftResponse toResponse(Aircraft aircraft);
}
