package com.basarsy.skyline.reservation.listener;

import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import com.basarsy.skyline.schedule.event.FlightCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlightCancellationListener {

    private final ReservationRepository reservationRepository;

    @EventListener
    @Transactional
    public void handleFlightCancelled(FlightCancelledEvent event) {
        log.info("Handling cancellation for flight: {}", event.flightId());

        reservationRepository.cancelReservationsForFlight(event.flightId(), ReservationStatus.CANCELLED);
        log.info("Bulk cancelled reservations for flight {}", event.flightId());
    }
}
