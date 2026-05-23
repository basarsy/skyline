package com.basarsy.skyline.checkin.controller;

import com.basarsy.skyline.checkin.dto.BoardingPassResponse;
import com.basarsy.skyline.checkin.dto.CheckInRequest;
import com.basarsy.skyline.checkin.service.CheckInService;
import com.basarsy.skyline.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
@Tag(name = "Check-In", description = "Online check-in and boarding pass management")
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping
    @Operation(summary = "Perform online check-in")
    public ResponseEntity<ApiResponse<BoardingPassResponse>> checkIn(@Valid @RequestBody CheckInRequest request) {
        BoardingPassResponse response = checkInService.performCheckIn(request);
        return ResponseEntity.ok(ApiResponse.success("Check-in successful", response));
    }

    @GetMapping("/{pnr}/boarding-pass")
    @Operation(summary = "Get boarding pass by PNR")
    public ResponseEntity<ApiResponse<BoardingPassResponse>> getBoardingPass(@PathVariable String pnr) {
        BoardingPassResponse response = checkInService.getBoardingPass(pnr);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
