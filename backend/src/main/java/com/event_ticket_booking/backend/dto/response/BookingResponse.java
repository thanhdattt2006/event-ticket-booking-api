package com.event_ticket_booking.backend.dto.response;

import com.event_ticket_booking.backend.entity.Booking;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BookingResponse {
    private Long id;
    private Booking.Status status;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    // We can add items later if needed
}
