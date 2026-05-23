package com.basarsy.skyline.checkin.service;

import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.dto.CheckInRequest;
import com.basarsy.skyline.checkin.entity.BoardingPass;
import com.basarsy.skyline.checkin.exception.CheckInWindowException;
import com.basarsy.skyline.checkin.mapper.BoardingPassMapper;
import com.basarsy.skyline.checkin.repository.BoardingPassRepository;
import com.basarsy.skyline.common.exception.ResourceNotFoundException;
import com.basarsy.skyline.reservation.entity.Reservation;
import com.basarsy.skyline.reservation.entity.ReservationStatus;
import com.basarsy.skyline.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final ReservationRepository reservationRepository;
    private final BoardingPassRepository boardingPassRepository;
    private final BoardingPassMapper boardingPassMapper;

    @Override
    @Transactional
    public BoardingPassResponse performCheckIn(CheckInRequest request) {
        Reservation reservation = reservationRepository.findByPnr(request.pnr())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with PNR: " + request.pnr()));

        validateCheckInWindow(reservation);
        validateReservationStatus(reservation);

        // Assign seat if not already assigned
        if (reservation.getSeatNumber() == null) {
            reservation.setSeatNumber(assignRandomSeat());
        }

        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservationRepository.save(reservation);

        BoardingPass boardingPass = createBoardingPass(reservation);
        boardingPass = boardingPassRepository.save(boardingPass);

        return boardingPassMapper.toResponse(boardingPass);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardingPassResponse getBoardingPass(String pnr) {
        BoardingPass boardingPass = boardingPassRepository.findByReservationPnr(pnr)
                .orElseThrow(() -> new ResourceNotFoundException("Boarding pass not found for PNR: " + pnr));
        return boardingPassMapper.toResponse(boardingPass);
    }

    private void validateCheckInWindow(Reservation reservation) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departureTime = reservation.getFlight().getDepartureTime();
        
        if (now.isBefore(departureTime.minusHours(48))) {
            throw new CheckInWindowException("Check-in opens 48 hours before departure.");
        }
        
        if (now.isAfter(departureTime.minusHours(1))) {
            throw new CheckInWindowException("Check-in closes 1 hour before departure.");
        }
    }

    private void validateReservationStatus(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CHECKED_IN) {
            throw new CheckInWindowException("Passenger is already checked in.");
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new CheckInWindowException("Check-in is only allowed for CONFIRMED reservations. Current status: " + reservation.getStatus());
        }
    }

    private String assignRandomSeat() {
        // Mock seat assignment logic: Rows 1-30, Seats A-F
        int row = (int) (Math.random() * 30) + 1;
        char seat = (char) ('A' + (int) (Math.random() * 6));
        return row + String.valueOf(seat);
    }

    private BoardingPass createBoardingPass(Reservation reservation) {
        BoardingPass bp = new BoardingPass();
        bp.setReservation(reservation);
        bp.setSeatNumber(reservation.getSeatNumber());
        bp.setGate("G" + ((int) (Math.random() * 20) + 1)); // Mock gate
        bp.setBoardingTime(reservation.getFlight().getDepartureTime().minusMinutes(45));
        bp.setBarcodeData("SKY-" + reservation.getPnr() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        bp.setIssuedAt(LocalDateTime.now());
        return bp;
    }
}
