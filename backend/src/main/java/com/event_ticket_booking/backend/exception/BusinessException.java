package com.event_ticket_booking.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a business rule is violated:
 * - sold out tickets
 * - voucher exhausted / expired / already used
 * - booking status transition not allowed
 * Maps to 409 Conflict — the request was valid but can't be fulfilled given current state.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
