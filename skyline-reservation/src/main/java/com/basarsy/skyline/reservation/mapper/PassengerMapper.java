package com.basarsy.skyline.reservation.mapper;

import com.basarsy.skyline.reservation.dto.PassengerRequest;
import com.basarsy.skyline.reservation.dto.PassengerResponse;
import com.basarsy.skyline.reservation.entity.Passenger;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PassengerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Passenger toEntity(PassengerRequest request);

    PassengerResponse toResponse(Passenger passenger);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget Passenger passenger, PassengerRequest request);
}
