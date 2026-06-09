package com.basarsy.skyline.route.mapper;

import com.basarsy.skyline.route.dto.RouteResponse;
import com.basarsy.skyline.route.entity.Route;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AirportMapper.class)
public interface RouteMapper {

    @Mapping(source = "origin", target = "origin")
    @Mapping(source = "destination", target = "destination")
    RouteResponse toResponse(Route route);
}
