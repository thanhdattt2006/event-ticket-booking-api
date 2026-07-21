package com.event_ticket_booking.backend.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConcertCreateRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotBlank(message = "Venue is required")
    private String venue;

    @NotNull(message = "Event date is required")
    @Future(message = "Event date must be in the future")
    private LocalDateTime eventDate;

    @NotNull(message = "Sale start time is required")
    private LocalDateTime saleStartAt;

    @NotNull(message = "Sale end time is required")
    private LocalDateTime saleEndAt;

    @NotNull(message = "Operator ID is required for creation")
    private Long createdBy;
}
