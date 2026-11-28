package com.basarsy.skyline.route.service;

import com.basarsy.skyline.route.dto.AirportRequest;
import com.basarsy.skyline.route.dto.AirportResponse;
import java.util.List;
import java.util.UUID;

public interface AirportService {

    List<AirportResponse> findAll();

    AirportResponse findById(UUID id);

    AirportResponse create(AirportRequest request);

    /** Sets an airport's time zone (an IANA region id, validated by the caller's request). */
    AirportResponse updateTimezone(UUID id, String timezone);
}
