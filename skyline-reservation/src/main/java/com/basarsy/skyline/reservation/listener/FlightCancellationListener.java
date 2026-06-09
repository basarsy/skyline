package com.basarsy.skyline.reservation.listener;

import com.basarsy.skyline.reservation.client.dto.FlightCancelledEvent;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
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

    @Transactional
    @KafkaListener(topics = "${kafka.topic.flight-cancelled}", groupId = "reservation-service")
    public void handleFlightCancelled(FlightCancelledEvent event) {
        log.info("Handling flight cancellation event for flight: {}", event.flightId());
        
        int updated = reservationRepository.updateStatusByFlightId(
                event.flightId(), ReservationStatus.CANCELLED);
                
        log.info("Updated {} reservations to CANCELLED for flight: {}", updated, event.flightId());
    }
}
