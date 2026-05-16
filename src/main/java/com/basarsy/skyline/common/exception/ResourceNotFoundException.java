package com.basarsy.skyline.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends SkylineException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
