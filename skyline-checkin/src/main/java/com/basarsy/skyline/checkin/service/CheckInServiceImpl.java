package com.basarsy.skyline.checkin.service;

import com.basarsy.skyline.checkin.client.ReservationClient;
import com.basarsy.skyline.checkin.client.dto.ReservationResponse;
import com.basarsy.skyline.checkin.client.dto.ReservationStatus;
import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.dto.CheckInRequest;
import com.basarsy.skyline.checkin.entity.BoardingPass;
import com.basarsy.skyline.checkin.exception.CheckInWindowException;
import com.basarsy.skyline.checkin.mapper.BoardingPassMapper;
import com.basarsy.skyline.checkin.repository.BoardingPassRepository;
import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.checkin.client.RouteClient;
import com.basarsy.skyline.checkin.client.dto.RouteResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final ReservationClient reservationClient;
    private final BoardingPassRepository boardingPassRepository;
    private final RouteClient routeClient;
    private final BoardingPassMapper boardingPassMapper;

    @Override
    @Transactional
    public BoardingPassResponse performCheckIn(CheckInRequest request) {
        ReservationResponse reservation = reservationClient.getReservationByPnr(request.pnr());

        validateCheckInWindow(reservation);
        validateReservationStatus(reservation);

        String seatNumber = reservation.seatNumber();
        if (seatNumber == null) {
            seatNumber = assignAvailableSeat(reservation.flight().getId());
        }

        // Update status via client
        reservationClient.updateStatus(reservation.id(), "CHECKED_IN", seatNumber);

        BoardingPass boardingPass = createBoardingPass(reservation, seatNumber);
        boardingPass = boardingPassRepository.save(boardingPass);

        return enrichBoardingPassResponse(boardingPass, reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardingPassResponse getBoardingPass(String pnr) {
        BoardingPass boardingPass = boardingPassRepository.findByReservationPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException("Boarding pass not found for PNR: " + pnr));
        
        ReservationResponse reservation = reservationClient.getReservation(boardingPass.getReservationId());
        return enrichBoardingPassResponse(boardingPass, reservation);
    }

    private BoardingPassResponse enrichBoardingPassResponse(BoardingPass boardingPass, ReservationResponse reservation) {
        BoardingPassResponse response = boardingPassMapper.toResponse(boardingPass);
        
        // Enrich from reservation data we already have
        response.setPnr(reservation.pnr());
        response.setPassengerName(reservation.passenger().firstName() + " " + reservation.passenger().lastName());
        response.setFlightNumber(reservation.flight().getFlightNumber());
        response.setDepartureTime(reservation.flight().getDepartureTime());

        try {
            RouteResponse route = routeClient.getRoute(reservation.flight().getRoute().id());
            response.setOrigin(route.origin().iataCode());
            response.setDestination(route.destination().iataCode());
        } catch (Exception e) {
            // Log error
        }
        
        return response;
    }

    private void validateCheckInWindow(ReservationResponse reservation) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureTime = reservation.flight().getDepartureTime();
        
        if (now.isBefore(departureTime.minusHours(48))) {
            throw new CheckInWindowException("Check-in opens 48 hours before departure.");
        }
        
        if (now.isAfter(departureTime.minusHours(1))) {
            throw new CheckInWindowException("Check-in closes 1 hour before departure.");
        }
    }

    private void validateReservationStatus(ReservationResponse reservation) {
        if (reservation.status() == ReservationStatus.CHECKED_IN) {
            throw new CheckInWindowException("Passenger is already checked in.");
        }
        if (reservation.status() != ReservationStatus.CONFIRMED) {
            throw new CheckInWindowException("Check-in is only allowed for CONFIRMED reservations. Current status: " + reservation.status());
        }
    }

    private String assignAvailableSeat(UUID flightId) {
        // In a real system, this would call an Inventory Service or Schedule Service
        // For now, let's just generate a random one.
        int row = (int) (Math.random() * 30) + 1;
        char seat = (char) ('A' + (int) (Math.random() * 6));
        return row + String.valueOf(seat);
    }

    private BoardingPass createBoardingPass(ReservationResponse reservation, String seatNumber) {
        BoardingPass bp = new BoardingPass();
        bp.setReservationId(reservation.id());
        bp.setSeatNumber(seatNumber);
        bp.setGate("G" + ((int) (Math.random() * 20) + 1)); // Mock gate
        bp.setBoardingTime(reservation.flight().getDepartureTime().minusMinutes(45));
        bp.setBarcodeData("SKY-" + reservation.pnr() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        bp.setIssuedAt(LocalDateTime.now());
        return bp;
    }
}
