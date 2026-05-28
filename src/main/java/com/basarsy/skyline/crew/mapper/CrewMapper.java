package com.basarsy.skyline.crew.mapper;

import com.basarsy.skyline.crew.dto.CrewMemberRequest;
import com.basarsy.skyline.crew.dto.CrewMemberResponse;
import com.basarsy.skyline.crew.dto.FlightCrewResponse;
import com.basarsy.skyline.crew.entity.CrewMember;
import com.basarsy.skyline.crew.entity.FlightCrew;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CrewMapper {

    @Mapping(target = "user.id", source = "userId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CrewMember toEntity(CrewMemberRequest request);

    @Mapping(target = "userId", source = "user.id")
    CrewMemberResponse toResponse(CrewMember entity);

    @Mapping(target = "crewMemberId", source = "crewMember.id")
    @Mapping(target = "employeeNumber", source = "crewMember.employeeNumber")
    @Mapping(target = "firstName", source = "crewMember.firstName")
    @Mapping(target = "lastName", source = "crewMember.lastName")
    @Mapping(target = "assignedRole", source = "role")
    FlightCrewResponse toFlightCrewResponse(FlightCrew entity);
}
