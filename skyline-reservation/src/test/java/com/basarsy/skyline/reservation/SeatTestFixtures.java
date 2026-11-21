package com.basarsy.skyline.reservation;

import com.basarsy.skyline.common.security.SkylineUserDetails;
import com.basarsy.skyline.reservation.client.dto.AircraftResponse;
import com.basarsy.skyline.reservation.client.dto.AircraftStatus;
import com.basarsy.skyline.reservation.client.dto.AircraftTypeSummary;
import com.basarsy.skyline.reservation.client.dto.AirportSummary;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
import com.basarsy.skyline.reservation.client.dto.RouteResponse;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Legroom;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.Seat;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatCategory;
import com.basarsy.skyline.reservation.client.dto.SeatLayout.SeatPosition;
import com.basarsy.skyline.reservation.dto.PassengerRequest;
import com.basarsy.skyline.reservation.entity.CabinClass;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A small two-cabin aircraft for seat tests: Business row 1 (2-2, A C | D F), Economy rows 2-5 (3-3). Row 2 is the
 * Economy bulkhead (EXTRA_LEGROOM), row 4 an exit row; the rest are window/aisle PREFERRED or middle STANDARD.
 */
public final class SeatTestFixtures {

    public static final UUID TYPE_ID = UUID.fromString("22222222-2222-2222-2222-222222222999");
    public static final int TOTAL_SEATS = 4 + 4 * 6;

    private SeatTestFixtures() {}

    public static SeatLayout layout() {
        List<Seat> seats = new ArrayList<>();
        for (String letter : List.of("A", "C", "D", "F")) {
            seats.add(new Seat("1" + letter, 1, letter, CabinClass.BUSINESS,
                    letter.equals("A") || letter.equals("F") ? SeatPosition.WINDOW : SeatPosition.AISLE,
                    Legroom.EXTRA, Set.of("BULKHEAD"), SeatCategory.EXTRA_LEGROOM));
        }
        for (int row = 2; row <= 5; row++) {
            for (String letter : List.of("A", "B", "C", "D", "E", "F")) {
                SeatPosition position = letter.equals("A") || letter.equals("F") ? SeatPosition.WINDOW
                        : letter.equals("C") || letter.equals("D") ? SeatPosition.AISLE : SeatPosition.MIDDLE;
                SeatCategory category = row == 2 ? SeatCategory.EXTRA_LEGROOM
                        : row == 4 ? SeatCategory.EXIT_ROW
                        : position == SeatPosition.MIDDLE ? SeatCategory.STANDARD : SeatCategory.PREFERRED;
                seats.add(new Seat(row + letter, row, letter, CabinClass.ECONOMY, position,
                        row == 2 || row == 4 ? Legroom.EXTRA : Legroom.STANDARD, Set.of(), category));
            }
        }
        return new SeatLayout(TYPE_ID, "Test", "TX-1", TOTAL_SEATS, "NARROW", null,
                List.of(new SeatLayout.Section(CabinClass.BUSINESS, 1, 1, List.of(List.of("A", "C"), List.of("D", "F"))),
                        new SeatLayout.Section(CabinClass.ECONOMY, 2, 5,
                                List.of(List.of("A", "B", "C"), List.of("D", "E", "F")))),
                List.of(), List.of(), seats);
    }

    public static FlightResponse flight(UUID id) {
        AirportSummary ist = new AirportSummary(UUID.randomUUID(), "IST", "Istanbul", "Istanbul", "Turkey",
                "Europe/Istanbul");
        AirportSummary lhr = new AirportSummary(UUID.randomUUID(), "LHR", "Heathrow", "London", "United Kingdom",
                "Europe/London");
        return FlightResponse.builder()
                .id(id)
                .flightNumber("SK" + Math.abs(id.hashCode() % 1000))
                .status(FlightStatus.SCHEDULED)
                .availableSeats(TOTAL_SEATS)
                .basePrice(new BigDecimal("100.00"))
                .departureTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(30))
                .aircraft(new AircraftResponse(UUID.randomUUID(), "TC-TST",
                        new AircraftTypeSummary(TYPE_ID, "Test", "TX-1", TOTAL_SEATS), AircraftStatus.ACTIVE, 2020))
                .route(new RouteResponse(UUID.randomUUID(), ist, lhr, 2500, 240))
                .build();
    }

    public static PassengerRequest passenger(String firstName) {
        return new PassengerRequest(firstName, "Tester", "P" + Math.abs(firstName.hashCode()),
                LocalDate.of(1990, 1, 1), "TR");
    }

    /** Runs as a passenger with this id. */
    public static void actAs(UUID userId) {
        var principal = new SkylineUserDetails(userId, userId + "@test.local", null,
                List.of(new SimpleGrantedAuthority("ROLE_PASSENGER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
