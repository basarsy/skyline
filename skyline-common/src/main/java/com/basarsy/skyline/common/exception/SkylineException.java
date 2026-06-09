package com.basarsy.skyline.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SkylineException extends RuntimeException {

    private final HttpStatus status;

    public SkylineException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
