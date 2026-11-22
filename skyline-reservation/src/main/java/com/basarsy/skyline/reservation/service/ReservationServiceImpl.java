package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
import com.basarsy.skyline.reservation.client.dto.SeatLayout;
import com.basarsy.skyline.reservation.dto.AncillaryLineResponse;
import com.basarsy.skyline.reservation.dto.GroupReservationRequest;
import com.basarsy.skyline.reservation.dto.GroupReservationResponse;
import com.basarsy.skyline.reservation.dto.PassengerRequest;
import com.basarsy.skyline.reservation.dto.PassengerResponse;
import com.basarsy.skyline.reservation.repository.ReservationAncillaryRepository;
import com.basarsy.skyline.reservation.dto.AncillaryPurchaseRequest;
import com.basarsy.skyline.reservation.entity.ReservationAncillary;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import com.basarsy.skyline.reservation.event.BookingEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.basarsy.skyline.reservation.dto.ReservationRequest;
import com.basarsy.skyline.reservation.dto.ReservationResponse;
import com.basarsy.skyline.reservation.entity.CabinClass;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.Passenger;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.mapper.PassengerMapper;
import com.basarsy.skyline.reservation.mapper.ReservationMapper;
import com.basarsy.skyline.reservation.repository.PassengerRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import com.basarsy.skyline.common.security.SkylineUserDetails;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private static final String PNR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PNR_LENGTH = 6;
    private static final int PNR_MAX_ATTEMPTS = 5;
    private static final SecureRandom PNR_RANDOM = new SecureRandom();
    private static final java.util.Set<String> PRIVILEGED_ROLES = java.util.Set.of("ROLE_STAFF", "ROLE_ADMIN", "ROLE_SERVICE");

    private final ReservationRepository reservationRepository;
    private final PassengerRepository passengerRepository;
    private final ScheduleClient scheduleClient;
    private final ReservationMapper reservationMapper;
    private final PassengerMapper passengerMapper;
    private final PricingProperties pricing;
    private final CabinInventory cabinInventory;
    private final SeatLayoutProvider seatLayoutProvider;
    private final SeatPricing seatPricing;
    private final SeatSelectionService seatSelectionService;
    private final ReservationAncillaryRepository ancillaryRepository;
    private final AncillaryService ancillaryService;
    private final BookingEvents bookingEvents;

    @Override
    @Transactional
    public ReservationResponse bookFlight(ReservationRequest request) {
        FlightResponse flight = bookableFlight(request.flightId(), 1);
        UUID currentUserId = getCurrentUserId();
        cabinInventory.reserve(flight, request.cabinClass(), 1);
        takeInventory(flight);

        FareType fareType = request.fareType() == null ? FareType.STANDARD : request.fareType();
        Reservation saved = createReservation(flight, request.passenger(), request.cabinClass(), fareType,
                currentUserId, null, contactEmail(request.contactEmail()));
        bookingEvents.booked(saved.getId(), List.of(new BookingEvents.Booked(saved, flight)));
        return enrichReservationResponse(saved, flight);
    }

    /**
     * Every passenger on every leg in one transaction: if any part fails (a seat hold expired, a cabin sold out),
     * nothing is booked and every flight's seats are given back.
     */
    @Override
    @Transactional
    public GroupReservationResponse bookGroup(GroupReservationRequest request) {
        validateGroup(request);
        UUID currentUserId = getCurrentUserId();
        String contactEmail = contactEmail(request.contactEmail());
        UUID groupId = UUID.randomUUID();
        int passengers = request.passengers().size();

        // Cabin locks are taken in one global order (flight, then cabin), whatever order the legs come in, so two
        // bookings of the same flights in opposite orders can't deadlock.
        Map<UUID, FlightResponse> flights = new HashMap<>();
        request.legs().stream()
                .sorted(Comparator.comparing((GroupReservationRequest.Leg leg) -> leg.flightId().toString())
                        .thenComparing(GroupReservationRequest.Leg::cabinClass))
                .forEach(leg -> {
                    FlightResponse flight = bookableFlight(leg.flightId(), passengers);
                    cabinInventory.reserve(flight, leg.cabinClass(), passengers);
                    flights.put(leg.flightId(), flight);
                });

        List<ReservationResponse> booked = new ArrayList<>();
        List<BookingEvents.Booked> events = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (GroupReservationRequest.Leg leg : request.legs()) {
            FlightResponse flight = flights.get(leg.flightId());
            FareType fareType = leg.fareType() == null ? FareType.STANDARD : leg.fareType();
            SeatLayout layout = null;
            SeatPricing.PriceList prices = null;

            for (int i = 0; i < passengers; i++) {
                takeInventory(flight);
                Reservation reservation = createReservation(flight, request.passengers().get(i), leg.cabinClass(),
                        fareType, currentUserId, groupId, contactEmail);
                String seat = seatFor(request, i, flight.getId());
                if (seat != null) {
                    if (layout == null) {
                        layout = seatLayoutProvider.requireForFlight(flight);
                        prices = seatPricing.priceList(flight, leg.cabinClass(), fareType);
                    }
                    seatSelectionService.applyHeldSeat(reservation, flight, layout, prices, seat,
                            SeatHoldStore.HoldOwner.forPassenger(currentUserId, i));
                }
                List<AncillaryPurchaseRequest.Item> extras = extrasFor(request, i, flight.getId());
                if (!extras.isEmpty()) {
                    ancillaryService.purchaseFor(reservation, flight, extras, SalesChannel.ONLINE);
                }
                ReservationResponse response = enrichReservationResponse(reservation, flight);
                booked.add(response);
                events.add(new BookingEvents.Booked(reservation, flight));
                total = total.add(reservation.getTotalPrice());
            }
        }
        // Totals include seats and add-ons bought above, so the event is recorded last.
        bookingEvents.booked(groupId, events);
        return new GroupReservationResponse(groupId, booked, total, pricing.currency());
    }

    private static void validateGroup(GroupReservationRequest request) {
        int passengers = request.passengers().size();
        Set<UUID> flights = new HashSet<>();
        for (GroupReservationRequest.Leg leg : request.legs()) {
            if (!flights.add(leg.flightId())) {
                throw new SkylineException("A flight can only appear once in a booking", HttpStatus.BAD_REQUEST);
            }
        }
        Set<String> passengerLegs = new HashSet<>();
        Set<String> seatsPerFlight = new HashSet<>();
        for (GroupReservationRequest.SeatSelection seat : request.seats()) {
            if (seat.passengerIndex() >= passengers || !flights.contains(seat.flightId())) {
                throw new SkylineException("A seat selection names a passenger or flight that is not in this booking",
                        HttpStatus.BAD_REQUEST);
            }
            if (!passengerLegs.add(seat.passengerIndex() + "/" + seat.flightId())) {
                throw new SkylineException("One seat per passenger per flight", HttpStatus.BAD_REQUEST);
            }
            if (!seatsPerFlight.add(seat.flightId() + "/" + seat.seatNumber())) {
                throw new SkylineException("Two passengers cannot have the same seat " + seat.seatNumber(),
                        HttpStatus.BAD_REQUEST);
            }
        }
        for (GroupReservationRequest.Extra extra : request.extras()) {
            if (extra.passengerIndex() >= passengers || !flights.contains(extra.flightId())) {
                throw new SkylineException("An add-on names a passenger or flight that is not in this booking",
                        HttpStatus.BAD_REQUEST);
            }
        }
    }

    private static List<AncillaryPurchaseRequest.Item> extrasFor(GroupReservationRequest request, int passengerIndex,
            UUID flightId) {
        return request.extras().stream()
                .filter(e -> e.passengerIndex() == passengerIndex && e.flightId().equals(flightId))
                .map(e -> new AncillaryPurchaseRequest.Item(e.productCode(), e.quantity()))
                .toList();
    }

    private static String seatFor(GroupReservationRequest request, int passengerIndex, UUID flightId) {
        return request.seats().stream()
                .filter(s -> s.passengerIndex() == passengerIndex && s.flightId().equals(flightId))
                .map(GroupReservationRequest.SeatSelection::seatNumber)
                .findFirst()
                .orElse(null);
    }

    private FlightResponse bookableFlight(UUID flightId, int passengers) {
        FlightResponse flight = scheduleClient.getFlight(flightId);
        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot book a flight that is not scheduled", HttpStatus.BAD_REQUEST);
        }
        if (flight.getAvailableSeats() < passengers) {
            throw new SkylineException(flight.getAvailableSeats() <= 0 ? "Flight is fully booked"
                    : "Only " + flight.getAvailableSeats() + " seats left on this flight", HttpStatus.BAD_REQUEST);
        }
        return flight;
    }

    /**
     * The seat lives in the schedule service, so it can't join this transaction. Hold it remotely, then give it
     * back if this transaction does not commit for ANY reason, including failures at commit time (e.g. a
     * constraint violation on flush), which a try/catch here can never see.
     */
    private void takeInventory(FlightResponse flight) {
        scheduleClient.decrementSeat(flight.getId());
        releaseSeatUnlessCommitted(flight.getId());
    }

    /** Where messages about a new booking go: the one given, else the account's email. */
    private static String contactEmail(String requested) {
        return requested != null && !requested.isBlank() ? requested.trim() : CallerAccess.currentUserEmail();
    }

    private Reservation createReservation(FlightResponse flight, PassengerRequest passengerRequest, CabinClass cabin,
            FareType fareType, UUID userId, UUID groupId, String contactEmail) {
        // Each booking gets its own passenger snapshot. Reusing one passenger per user meant that
        // booking for someone else rewrote the name/passport on all of the user's past reservations.
        Passenger passenger = passengerMapper.toEntity(passengerRequest);
        passenger.setUserId(userId);
        passenger = passengerRepository.save(passenger);

        Reservation reservation = new Reservation();
        reservation.setCabinClass(cabin);
        reservation.setFlightId(flight.getId());
        reservation.setPassenger(passenger);
        reservation.setBookedAt(LocalDateTime.now());
        reservation.setPnr(generateUniquePnr());
        reservation.setFareType(fareType);
        reservation.setFarePrice(calculatePrice(flight, cabin, fareType));
        reservation.setTotalPrice(reservation.getFarePrice());
        reservation.setBookingGroupId(groupId);
        reservation.setContactEmail(contactEmail);

        // In a real system, payment processing would happen here, and status would be CONFIRMED upon success
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservationRepository.saveAndFlush(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID id) {
        Reservation reservation = getReservationEntity(id);
        verifyOwnership(reservation);
        return enrichReservationResponse(reservation, null);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationByPnr(String pnr, String lastName) {
        Reservation reservation = reservationRepository.findByPnr(pnr.trim().toUpperCase())
                .orElseThrow(ReservationServiceImpl::pnrNotFound);

        // Staff, internal services and the booking owner see the full reservation.
        if (isPrivilegedCaller() || isOwner(reservation)) {
            return enrichReservationResponse(reservation, null);
        }

        // Anyone else needs PNR + last name ("manage booking") and gets a view without sensitive
        // identity data. A wrong last name is indistinguishable from a non-existent PNR, so the
        // endpoint cannot be used to confirm which PNRs exist.
        if (lastName != null && lastName.trim().equalsIgnoreCase(reservation.getPassenger().getLastName().trim())) {
            ReservationResponse response = enrichReservationResponse(reservation, null);
            response.setPassenger(redacted(response.getPassenger()));
            response.setContactEmail(null);
            return response;
        }
        throw pnrNotFound();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(Pageable pageable) {
        // A user with no bookings yet gets an empty page, not a 404.
        Page<Reservation> page = reservationRepository.findByPassenger_UserId(getCurrentUserId(), pageable);
        // Every add-on line of the page in one query, not one per reservation.
        Map<UUID, List<AncillaryLineResponse>> lines = page.isEmpty() ? Map.of()
                : ancillaryRepository.findByReservation_IdInOrderByCreatedAtAsc(
                                page.getContent().stream().map(Reservation::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(line -> line.getReservation().getId(),
                                Collectors.mapping(AncillaryLineResponse::of, Collectors.toList())));
        return page.map(r -> enrichReservationResponse(r, null, lines.getOrDefault(r.getId(), List.of())));
    }

    @Override
    @Transactional
    public void cancelReservation(UUID id) {
        Reservation reservation = getReservationEntity(id);
        verifyOwnership(reservation);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new SkylineException("Reservation is already cancelled", HttpStatus.BAD_REQUEST);
        }
        
        FlightResponse flight = scheduleClient.getFlight(reservation.getFlightId());
        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            throw new SkylineException("Cannot cancel reservation for a flight that has departed or is boarding", HttpStatus.BAD_REQUEST);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        // Free the seat number so it can be assigned again ((flight_id, seat_number) is unique).
        reservation.setSeatNumber(null);
        // Add-ons end with the booking: refundable ones (FLEX fares, always-refundable products such as meals)
        // are paid back, the rest are forfeited.
        ancillaryRepository.findByReservation_IdAndStatus(reservation.getId(), ReservationAncillary.Status.ACTIVE)
                .forEach(ReservationAncillary::end);
        ancillaryRepository.flush();
        seatSelectionService.recalculateTotal(reservation);
        reservationRepository.save(reservation);
        bookingEvents.cancelled(reservation, flight);

        // Return the seat only once the cancellation is durable. Releasing it before commit could
        // oversell the flight if the commit then failed.
        releaseSeatAfterCommit(reservation.getFlightId());
    }

    private ReservationResponse enrichReservationResponse(Reservation reservation, FlightResponse flight) {
        List<AncillaryLineResponse> lines = reservation.getId() == null ? List.of()
                : ancillaryRepository.findByReservation_IdOrderByCreatedAtAsc(reservation.getId()).stream()
                        .map(AncillaryLineResponse::of).toList();
        return enrichReservationResponse(reservation, flight, lines);
    }

    private ReservationResponse enrichReservationResponse(Reservation reservation, FlightResponse flight,
            List<AncillaryLineResponse> lines) {
        ReservationResponse response = reservationMapper.toResponse(reservation);
        response.setCurrency(pricing.currency());
        response.setAncillaries(lines);
        if (flight == null) {
            try {
                flight = scheduleClient.getFlight(reservation.getFlightId());
            } catch (Exception e) {
                // Log error
            }
        }
        response.setFlight(flight);
        return response;
    }

    private Reservation getReservationEntity(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));
    }

    private UUID getCurrentUserId() {
        return CallerAccess.currentUserId();
    }

    private void verifyOwnership(Reservation reservation) {
        CallerAccess.verifyOwnership(reservation);
    }

    private static boolean isPrivilegedCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> PRIVILEGED_ROLES.contains(a.getAuthority()));
    }

    private static boolean isOwner(Reservation reservation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof SkylineUserDetails user
                && user.getId() != null
                && user.getId().equals(reservation.getPassenger().getUserId());
    }

    private static ResourceNotFoundException pnrNotFound() {
        return new ResourceNotFoundException("Reservation not found");
    }

    /** Passenger view for unauthenticated PNR + last name lookups: no DOB, no owner id, masked passport. */
    static PassengerResponse redacted(PassengerResponse passenger) {
        if (passenger == null) {
            return null;
        }
        String passport = passenger.passportNumber();
        String masked = passport == null || passport.length() <= 3
                ? "***"
                : "*".repeat(passport.length() - 3) + passport.substring(passport.length() - 3);
        return new PassengerResponse(passenger.id(), null, passenger.firstName(), passenger.lastName(), masked,
                null, passenger.nationality());
    }

    static BigDecimal calculatePrice(FlightResponse flight, CabinClass cabinClass, FareType fareType) {
        BigDecimal multiplier = switch (cabinClass) {
            case ECONOMY -> BigDecimal.valueOf(1.0);
            case BUSINESS -> BigDecimal.valueOf(2.5);
            case FIRST -> BigDecimal.valueOf(4.0);
        };

        // Money has two decimals: multiplying by 2.5 and 1.15 would otherwise store e.g. 287.500.
        return flight.getBasePrice().multiply(multiplier).multiply(fareType.multiplier())
                .multiply(LoadFactorPricing.multiplier(flight))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void releaseSeatUnlessCommitted(UUID flightId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    releaseSeat(flightId, "booking transaction did not commit");
                }
            }
        });
    }

    private void releaseSeatAfterCommit(UUID flightId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                releaseSeat(flightId, "reservation cancelled");
            }
        });
    }

    private void releaseSeat(UUID flightId, String reason) {
        try {
            scheduleClient.incrementSeat(flightId);
        } catch (Exception ex) {
            // Runs after the transaction finished, so there is nothing left to roll back. Log loudly with a
            // stable marker so the seat count can be reconciled (alerting / a retry job can key off it).
            log.error("SEAT_RELEASE_FAILED flightId={} reason={}: seat inventory needs reconciliation",
                    flightId, reason, ex);
        }
    }

    /**
     * 6-character PNR from SecureRandom over 32 unambiguous characters (no 0/O, 1/I): about 1.07 billion
     * values instead of 16.7 million hex values from a non-cryptographic source, with a collision check.
     */
    private String generateUniquePnr() {
        for (int attempt = 0; attempt < PNR_MAX_ATTEMPTS; attempt++) {
            StringBuilder pnr = new StringBuilder(PNR_LENGTH);
            for (int i = 0; i < PNR_LENGTH; i++) {
                pnr.append(PNR_ALPHABET.charAt(PNR_RANDOM.nextInt(PNR_ALPHABET.length())));
            }
            String candidate = pnr.toString();
            if (!reservationRepository.existsByPnr(candidate)) {
                return candidate;
            }
        }
        throw new SkylineException("Could not allocate a booking reference, please retry", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
