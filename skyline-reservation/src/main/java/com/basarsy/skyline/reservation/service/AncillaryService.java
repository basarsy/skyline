package com.basarsy.skyline.reservation.service;

import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.common.pricing.PricingProperties;
import com.basarsy.skyline.reservation.client.ScheduleClient;
import com.basarsy.skyline.reservation.client.dto.FlightResponse;
import com.basarsy.skyline.reservation.client.dto.FlightStatus;
import com.basarsy.skyline.reservation.client.dto.RouteResponse;
import com.basarsy.skyline.reservation.dto.AncillaryOffer;
import com.basarsy.skyline.reservation.dto.AncillaryOptionsResponse;
import com.basarsy.skyline.reservation.dto.AncillaryPurchaseRequest;
import com.basarsy.skyline.reservation.entity.AncillaryPrice;
import com.basarsy.skyline.reservation.entity.AncillaryProduct;
import com.basarsy.skyline.reservation.entity.AncillaryType;
import com.basarsy.skyline.reservation.entity.FareAllowance;
import com.basarsy.skyline.reservation.entity.FareType;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationAncillary;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.entity.RouteScope;
import com.basarsy.skyline.reservation.entity.SalesChannel;
import com.basarsy.skyline.reservation.repository.AncillaryPriceRepository;
import com.basarsy.skyline.reservation.repository.AncillaryProductRepository;
import com.basarsy.skyline.reservation.repository.FareAllowanceRepository;
import com.basarsy.skyline.reservation.repository.ReservationAncillaryRepository;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bags and other add-ons, per passenger per flight, at booking, in manage booking and at check-in. Every price is
 * computed here from the catalog; what the fare includes is free and can't be bought again ({@link AncillaryRules}).
 */
@Service
@RequiredArgsConstructor
public class AncillaryService {

    private static final Set<ReservationStatus> SELLABLE = EnumSet.of(ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN);
    private static final Set<FlightStatus> OPEN = EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED);

    private final ReservationRepository reservationRepository;
    private final ReservationAncillaryRepository lines;
    private final AncillaryProductRepository products;
    private final AncillaryPriceRepository prices;
    private final FareAllowanceRepository allowances;
    private final ScheduleClient scheduleClient;
    private final SeatSelectionService seatSelection;
    private final PricingProperties pricing;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AncillaryOptionsResponse options(UUID reservationId, SalesChannel channel) {
        Reservation reservation = owned(reservationId);
        SalesChannel ch = channel(channel);
        FlightResponse flight = scheduleClient.getFlight(reservation.getFlightId());
        RouteScope scope = scopeOf(flight);
        List<AncillaryProduct> catalog = products.findAllByOrderBySortOrderAsc();
        Map<String, Integer> included = included(reservation);
        Map<String, AncillaryProduct> byCode = catalog.stream()
                .collect(Collectors.toMap(AncillaryProduct::getCode, Function.identity()));

        List<AncillaryOptionsResponse.Included> includedView = included.entrySet().stream()
                .filter(e -> byCode.containsKey(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new AncillaryOptionsResponse.Included(e.getKey(), byCode.get(e.getKey()).getName(), e.getValue()))
                .toList();
        List<AncillaryOffer> offers = offers(reservation, flight, catalog, included, ch);
        return new AncillaryOptionsResponse(reservationId, flight.getId(), scope, ch, pricing.currency(), includedView,
                offers);
    }

    @Transactional
    public Reservation purchase(UUID reservationId, AncillaryPurchaseRequest request) {
        Reservation reservation = owned(reservationId);
        FlightResponse flight = scheduleClient.getFlight(reservation.getFlightId());
        purchaseFor(reservation, flight, request.items(), channel(request.channel()));
        return reservation;
    }

    /** Removes a bought add-on before its sales cut-off: refunded when refundable, otherwise just cancelled. */
    @Transactional
    public Reservation remove(UUID reservationId, UUID lineId) {
        Reservation reservation = owned(reservationId);
        ReservationAncillary line = lines.findById(lineId)
                .filter(l -> l.getReservation().getId().equals(reservationId))
                .orElseThrow(() -> new ResourceNotFoundException("Add-on not found on this reservation"));
        if (line.getType() == AncillaryType.SEAT) {
            throw new SkylineException("Use the seat endpoints to change or remove a seat", HttpStatus.BAD_REQUEST);
        }
        if (line.getStatus() != ReservationAncillary.Status.ACTIVE) {
            throw new SkylineException("This add-on was already removed", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        FlightResponse flight = scheduleClient.getFlight(reservation.getFlightId());
        AncillaryProduct product = products.findById(line.getProductCode()).orElse(null);
        int cutoff = product == null ? 1 : product.getCutoffHours();
        if (!LocalDateTime.now(clock).isBefore(flight.getDepartureTime().minusHours(cutoff))) {
            throw new SkylineException("Add-ons can't be removed this close to departure", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (line.getType() == AncillaryType.CHECKED_BAG) {
            requireBagForEveryTopUp(reservation, line.getQuantity());
        }
        line.end();
        lines.saveAndFlush(line);
        seatSelection.recalculateTotal(reservation);
        return reservationRepository.saveAndFlush(reservation);
    }

    /**
     * Prices and adds the items (each validated against what is included and already bought, top-ups after bags),
     * then updates the total. Runs inside the caller's transaction (booking, or {@link #purchase}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void purchaseFor(Reservation reservation, FlightResponse flight, List<AncillaryPurchaseRequest.Item> items,
            SalesChannel channel) {
        if (!SELLABLE.contains(reservation.getStatus()) || flight == null || !OPEN.contains(flight.getStatus())) {
            throw new SkylineException("Add-ons can't be bought for this booking any more",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<AncillaryProduct> catalog = products.findAllByOrderBySortOrderAsc();
        Map<String, AncillaryProduct> byCode = catalog.stream()
                .collect(Collectors.toMap(AncillaryProduct::getCode, Function.identity()));
        Map<String, Integer> included = included(reservation);
        Map<String, Integer> purchased = purchased(reservation);
        Map<String, AncillaryPrice> priceList = priceList(scopeOf(flight));
        boolean refundableFare = reservation.getFareType() == FareType.FLEX;

        // Top-ups go last, so the basket is valid in any order: a top-up is checked against every bag it carries.
        List<AncillaryPurchaseRequest.Item> ordered = items.stream()
                .sorted(Comparator.comparing((AncillaryPurchaseRequest.Item i) -> byCode.get(i.productCode()) != null
                        && byCode.get(i.productCode()).getType() == AncillaryType.EXTRA_WEIGHT))
                .toList();
        for (AncillaryPurchaseRequest.Item item : ordered) {
            AncillaryProduct product = byCode.get(item.productCode());
            if (product == null || !product.isActive()) {
                throw new SkylineException("Unknown add-on: " + item.productCode(), HttpStatus.UNPROCESSABLE_ENTITY);
            }
            // The whole catalog, not just this product: caps span types (e.g. bags of every size).
            AncillaryOffer offer = AncillaryRules.offers(catalog, priceList, included, purchased,
                            flight.getDepartureTime(), LocalDateTime.now(clock), channel).stream()
                    .filter(o -> o.code().equals(product.getCode()))
                    .findFirst()
                    .orElseThrow();
            if (!offer.available() || item.quantity() > offer.maxAdditional()) {
                throw new SkylineException(product.getName() + ": " + (offer.reason() != null ? offer.reason()
                        : "at most " + offer.maxAdditional() + " more on this flight"), HttpStatus.UNPROCESSABLE_ENTITY);
            }

            ReservationAncillary line = new ReservationAncillary();
            line.setReservation(reservation);
            line.setType(product.getType());
            line.setProductCode(product.getCode());
            line.setDescription(product.getName());
            line.setQuantity(item.quantity());
            line.setPrice(offer.price().multiply(BigDecimal.valueOf(item.quantity())));
            line.setChannel(channel);
            line.setRefundable(refundableFare || product.isAlwaysRefundable());
            lines.save(line);
            purchased.merge(product.getCode(), item.quantity(), Integer::sum);
        }
        lines.flush();
        seatSelection.recalculateTotal(reservation);
        reservationRepository.saveAndFlush(reservation);
    }

    /** An extra-weight top-up needs a checked bag: a bag can't go while it would leave a top-up without one. */
    private void requireBagForEveryTopUp(Reservation reservation, int bagsRemoved) {
        Map<String, AncillaryType> types = products.findAll().stream()
                .collect(Collectors.toMap(AncillaryProduct::getCode, AncillaryProduct::getType));
        Map<AncillaryType, Integer> count = new HashMap<>();
        included(reservation).forEach((code, quantity) -> count.merge(types.get(code), quantity, Integer::sum));
        purchased(reservation).forEach((code, quantity) -> count.merge(types.get(code), quantity, Integer::sum));
        int bagsLeft = count.getOrDefault(AncillaryType.CHECKED_BAG, 0) - bagsRemoved;
        if (count.getOrDefault(AncillaryType.EXTRA_WEIGHT, 0) > bagsLeft) {
            throw new SkylineException("Remove the extra weight on this bag first", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** Domestic when both airports are in the same country; international otherwise (or when unknown). */
    static RouteScope scopeOf(FlightResponse flight) {
        RouteResponse route = flight.getRoute();
        if (route == null || route.origin() == null || route.destination() == null
                || route.origin().country() == null) {
            return RouteScope.INTERNATIONAL;
        }
        return route.origin().country().equalsIgnoreCase(route.destination().country())
                ? RouteScope.DOMESTIC : RouteScope.INTERNATIONAL;
    }

    private List<AncillaryOffer> offers(Reservation reservation, FlightResponse flight, List<AncillaryProduct> catalog,
            Map<String, Integer> included, SalesChannel channel) {
        List<AncillaryOffer> offers = AncillaryRules.offers(catalog, priceList(scopeOf(flight)), included,
                purchased(reservation), flight.getDepartureTime(), LocalDateTime.now(clock), channel);
        if (!SELLABLE.contains(reservation.getStatus()) || !OPEN.contains(flight.getStatus())) {
            return offers.stream().map(o -> new AncillaryOffer(o.code(), o.type(), o.name(), o.description(),
                    o.weightKg(), o.includedQuantity(), o.purchasedQuantity(), 0, o.price(), o.onlinePrice(),
                    o.airportPrice(), false, "Add-ons can't be bought for this booking any more")).toList();
        }
        return offers;
    }

    private Map<String, AncillaryPrice> priceList(RouteScope scope) {
        return prices.findByRouteScope(scope).stream()
                .collect(Collectors.toMap(AncillaryPrice::getProductCode, Function.identity()));
    }

    private Map<String, Integer> included(Reservation reservation) {
        return allowances.findByCabinClassAndFareType(reservation.getCabinClass(), reservation.getFareType()).stream()
                .collect(Collectors.toMap(FareAllowance::getProductCode, FareAllowance::getQuantity, Integer::sum));
    }

    private Map<String, Integer> purchased(Reservation reservation) {
        Map<String, Integer> purchased = new HashMap<>();
        if (reservation.getId() != null) {
            lines.findByReservation_IdAndStatus(reservation.getId(), ReservationAncillary.Status.ACTIVE).stream()
                    .filter(line -> line.getType() != AncillaryType.SEAT)
                    .forEach(line -> purchased.merge(line.getProductCode(), line.getQuantity(), Integer::sum));
        }
        return purchased;
    }

    private Reservation owned(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        CallerAccess.verifyOwnership(reservation);
        return reservation;
    }

    /** Airport prices are for the airport desk: staff and admins only. */
    private static SalesChannel channel(SalesChannel requested) {
        if (requested == SalesChannel.AIRPORT && !CallerAccess.isStaffOrAdmin()) {
            throw new SkylineException("Airport sales are made at the desk by staff", HttpStatus.FORBIDDEN);
        }
        return requested == null ? SalesChannel.ONLINE : requested;
    }
}
