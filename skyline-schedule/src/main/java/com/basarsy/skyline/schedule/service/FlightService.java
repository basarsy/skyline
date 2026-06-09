package com.basarsy.skyline.schedule.service;

import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightService {
    FlightResponse createFlight(FlightRequest request);
    FlightResponse updateFlight(UUID id, FlightRequest request);
    FlightResponse updateFlightStatus(UUID id, FlightStatus status);
    FlightResponse getFlight(UUID id);
    Page<FlightResponse> searchFlights(String origin, String destination, LocalDate date, Pageable pageable);
    void cancelFlight(UUID id);

    void decrementSeat(UUID id);

    void incrementSeat(UUID id);
}
