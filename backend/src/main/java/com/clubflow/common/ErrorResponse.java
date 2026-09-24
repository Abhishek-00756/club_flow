package com.clubflow.common;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ErrorResponse(Instant timestamp, int status, String error, String message,
                            Map<String, String> fieldErrors) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, Map.of());
    }

    public static ErrorResponse of(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, fieldErrors);
    }
}
