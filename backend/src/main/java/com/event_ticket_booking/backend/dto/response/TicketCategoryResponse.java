package com.event_ticket_booking.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TicketCategoryResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer quantityTotal;
    private Integer quantitySold;
}
