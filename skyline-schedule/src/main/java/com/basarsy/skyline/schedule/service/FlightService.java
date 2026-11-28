package com.basarsy.skyline.schedule.service;

import com.basarsy.skyline.schedule.dto.FlightBoardFilter;
import com.basarsy.skyline.schedule.dto.FlightRequest;
import com.basarsy.skyline.schedule.dto.FlightResponse;
import com.basarsy.skyline.schedule.entity.FlightStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightService {
    FlightResponse createFlight(FlightRequest request);
    FlightResponse updateFlight(UUID id, FlightRequest request);
    FlightResponse updateFlightStatus(UUID id, FlightStatus status);

    /** Sets the departure time ops now expect; the flight becomes DELAYED. */
    FlightResponse delayFlight(UUID id, LocalDateTime estimatedDepartureTime);

    /** Lifts the delay: no expected time, and a DELAYED flight is SCHEDULED again. */
    FlightResponse clearDelay(UUID id);

    FlightResponse assignGate(UUID id, String gate);
    FlightResponse getFlight(UUID id);
    Page<FlightResponse> searchFlights(String origin, String destination, LocalDate date, Pageable pageable);

    /** Operations listing: flights of every status, filtered by {@code filter}. */
    Page<FlightResponse> getFlightBoard(FlightBoardFilter filter, Pageable pageable);
    void cancelFlight(UUID id);

    /** Up to {@code limit} flight numbers of the aircraft's flights that are not cancelled or arrived yet. */
    List<String> flightsOnAircraft(UUID aircraftId, int limit);

    /** Flight numbers of the flights among {@code flightIds} that occupy their crew while {@code flightId} flies. */
    List<String> overlapping(UUID flightId, List<UUID> flightIds);

    void incrementSeat(UUID id);

    /**
     * Holds one seat for {@code claimId}, once: claiming the same id again changes nothing, so a call whose
     * response was lost can be repeated. Refused when the flight is full, its sales have closed or it isn't
     * bookable.
     */
    void claimSeat(UUID flightId, UUID claimId);

    /** Gives back the seat {@code claimId} holds; nothing when it holds none (never landed, already released). */
    void releaseSeat(UUID flightId, UUID claimId);
}
