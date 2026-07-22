package com.event_ticket_booking.backend.dto.request;

import com.event_ticket_booking.backend.entity.Booking;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingStatusUpdateRequest {
    @NotNull(message = "New status is required")
    private Booking.Status newStatus;
    
    private String reason;
    
    @NotNull(message = "Operator ID is required")
    private Long operatorId;
}
