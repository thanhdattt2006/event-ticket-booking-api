package com.event_ticket_booking.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BookingCreateRequest {

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotNull(message = "Concert ID is required")
    private Long concertId;

    private String voucherCode;

    @NotEmpty(message = "Booking items must not be empty")
    @Valid
    private List<BookingItemRequest> items;
}
