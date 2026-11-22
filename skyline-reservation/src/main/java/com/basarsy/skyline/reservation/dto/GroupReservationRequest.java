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
 * be held by the caller for that passenger ({@code POST /reservations/seat-holds}). {@code contactEmail} is where
 * messages about the booking go; the account's email when omitted.
 */
public record GroupReservationRequest(
        @NotEmpty @Size(max = 9) @Valid List<PassengerRequest> passengers,
        @NotEmpty @Size(max = 4) @Valid List<Leg> legs,
        @Size(max = 36) @Valid List<SeatSelection> seats,
        @Size(max = 100) @Valid List<Extra> extras,
        @jakarta.validation.constraints.Email(message = "Contact email must be a valid email address")
        @Size(max = 254) String contactEmail) {

    /** Without add-ons (the shape before add-ons could be bought at booking). */
    public GroupReservationRequest(List<PassengerRequest> passengers, List<Leg> legs, List<SeatSelection> seats) {
        this(passengers, legs, seats, null, null);
    }

    /** Contact email left to default to the account's. */
    public GroupReservationRequest(List<PassengerRequest> passengers, List<Leg> legs, List<SeatSelection> seats,
            List<Extra> extras) {
        this(passengers, legs, seats, extras, null);
    }

    /** An add-on for one passenger on one leg, e.g. a 20 kg bag; priced on the server. */
    public record Extra(
            @Min(0) @Max(8) int passengerIndex,
            @NotNull UUID flightId,
            @jakarta.validation.constraints.NotBlank String productCode,
            @Min(1) @Max(3) int quantity) {}

    public List<Extra> extras() {
        return extras == null ? List.of() : extras;
    }

    public record Leg(@NotNull UUID flightId, @NotNull CabinClass cabinClass, FareType fareType) {}

    public record SeatSelection(
            @Min(0) @Max(8) int passengerIndex,
            @NotNull UUID flightId,
            @NotNull @Pattern(regexp = "\\d{1,2}[A-Z]", message = "seatNumber must look like 12A") String seatNumber) {}

    public List<SeatSelection> seats() {
        return seats == null ? List.of() : seats;
    }
}
