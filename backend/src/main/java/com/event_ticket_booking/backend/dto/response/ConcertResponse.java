package com.event_ticket_booking.backend.dto.response;

import com.event_ticket_booking.backend.entity.Concert;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConcertResponse {
    private Long id;
    private String name;
    private String description;
    private String venue;
    private LocalDateTime eventDate;
    private LocalDateTime saleStartAt;
    private LocalDateTime saleEndAt;
    private Concert.Status status;
    private Long createdBy;
    private List<TicketCategoryResponse> ticketCategories;
}
