package com.event_ticket_booking.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the ticket reserve atomic UPDATE returns 0 affected rows
 * (quantity_total - quantity_sold < qty requested).
 * Kept separate from BusinessException so callers can catch it specifically.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientTicketException extends BusinessException {
    public InsufficientTicketException(String message) {
        super(message);
    }
}
