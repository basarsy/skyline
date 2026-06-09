package com.basarsy.skyline.route.mapper;

import com.basarsy.skyline.route.dto.AirportRequest;
import com.basarsy.skyline.route.dto.AirportResponse;
import com.basarsy.skyline.route.dto.AirportSummary;
import com.basarsy.skyline.route.entity.Airport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AirportMapper {

    AirportResponse toResponse(Airport airport);

    AirportSummary toSummary(Airport airport);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Airport toEntity(AirportRequest request);
}
