package com.event_ticket_booking.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class VoucherValidationResponse {
    private boolean valid;
    private String message;
    private String code;
    private String discountType;
    private BigDecimal discountValue;
}
