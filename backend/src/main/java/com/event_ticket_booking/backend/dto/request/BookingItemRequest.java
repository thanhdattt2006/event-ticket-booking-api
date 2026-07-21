package com.event_ticket_booking.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingItemRequest {

    @NotNull(message = "Ticket category ID is required")
    private Long ticketCategoryId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity;
}
