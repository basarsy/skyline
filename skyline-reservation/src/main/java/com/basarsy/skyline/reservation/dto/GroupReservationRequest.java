package com.basarsy.skyline.reservation.dto;

import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Several passengers on one or more flights (a round trip, a connection), booked together: one reservation per
 * passenger per leg, all sharing a booking group. Each passenger may have one seat per leg; a chosen seat must
 * be held by the caller for that passenger ({@code POST /reservations/seat-holds}).
 */
public record GroupReservationRequest(
        @NotEmpty @Size(max = 9) @Valid List<PassengerRequest> passengers,
        @NotEmpty @Size(max = 4) @Valid List<Leg> legs,
        @Size(max = 36) @Valid List<SeatSelection> seats) {

    public record Leg(@NotNull UUID flightId, @NotNull CabinClass cabinClass, FareType fareType) {}

    public record SeatSelection(
            @Min(0) @Max(8) int passengerIndex,
            @NotNull UUID flightId,
            @NotNull @Pattern(regexp = "\\d{1,2}[A-Z]", message = "seatNumber must look like 12A") String seatNumber) {}

    public List<SeatSelection> seats() {
        return seats == null ? List.of() : seats;
    }
}
