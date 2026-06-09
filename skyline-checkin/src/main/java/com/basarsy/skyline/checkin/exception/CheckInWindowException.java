package com.basarsy.skyline.checkin.exception;

import com.basarsy.skyline.common.exception.SkylineException;
import org.springframework.http.HttpStatus;

public class CheckInWindowException extends SkylineException {
    public CheckInWindowException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
