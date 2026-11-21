package com.basarsy.skyline.reservation.listener;

import com.basarsy.skyline.reservation.client.dto.FlightCancelledEvent;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightCancellationListener {

    private final ReservationRepository reservationRepository;
    private final com.basarsy.skyline.reservation.repository.ReservationAncillaryRepository ancillaryRepository;

    @Transactional
    @KafkaListener(topics = "${kafka.topic.flight-cancelled:skyline.flight.cancelled}", groupId = "reservation-service")
    public void handleFlightCancelled(FlightCancelledEvent event) {
        log.info("Handling flight cancellation event for flight: {}", event.flightId());
        
        LocalDateTime now = LocalDateTime.now();
        // The airline's cancellation: seat fees and bags are refunded on every fare.
        int refunded = ancillaryRepository.refundAllOnFlight(event.flightId(), now);
        ancillaryRepository.recalculateTotalsOnFlight(event.flightId());
        int updated = reservationRepository.updateStatusByFlightId(event.flightId(), ReservationStatus.CANCELLED, now);
        log.info("Refunded {} add-ons on cancelled flight {}", refunded, event.flightId());

        log.info("Updated {} reservations to CANCELLED for flight: {}", updated, event.flightId());
    }
}
