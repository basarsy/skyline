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

        var reservations = reservationRepository.findByFlight_Id(event.flightId());

        reservations.stream()
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED)
                .forEach(r -> {
                    r.setStatus(ReservationStatus.CANCELLED);
                    reservationRepository.save(r);
                    log.info("Cancelled reservation {} for cancelled flight {}", r.getPnr(), event.flightId());
                });
    }
}
