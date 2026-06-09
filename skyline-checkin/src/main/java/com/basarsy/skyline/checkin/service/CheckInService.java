package com.basarsy.skyline.checkin.service;

import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.dto.CheckInRequest;

public interface CheckInService {
    BoardingPassResponse performCheckIn(CheckInRequest request);
    BoardingPassResponse getBoardingPass(String pnr);
}
