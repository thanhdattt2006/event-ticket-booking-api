package com.event_ticket_booking.backend.controller.operation;

import com.event_ticket_booking.backend.dto.request.ConcertCreateRequest;
import com.event_ticket_booking.backend.dto.request.TicketCategoryCreateRequest;
import com.event_ticket_booking.backend.dto.response.ApiResponse;
import com.event_ticket_booking.backend.dto.response.ConcertResponse;
import com.event_ticket_booking.backend.dto.response.TicketCategoryResponse;
import com.event_ticket_booking.backend.service.ConcertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operation/concerts")
@RequiredArgsConstructor
@Tag(name = "Operation - Concerts", description = "Concert management APIs for operators")
public class ConcertOperationController {

    private final ConcertService concertService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new concert", description = "Creates a new concert with DRAFT status")
    public ApiResponse<ConcertResponse> createConcert(@Valid @RequestBody ConcertCreateRequest request) {
        ConcertResponse response = concertService.createConcert(request);
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{id}/publish")
    @Operation(summary = "Publish a concert", description = "Transitions a DRAFT concert to PUBLISHED status")
    public ApiResponse<ConcertResponse> publishConcert(@PathVariable Long id) {
        ConcertResponse response = concertService.publishConcert(id);
        return ApiResponse.ok(response);
    }

    @PostMapping("/{id}/ticket-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add ticket category", description = "Adds a new ticket category to a DRAFT concert")
    public ApiResponse<TicketCategoryResponse> addTicketCategory(
            @PathVariable Long id,
            @Valid @RequestBody TicketCategoryCreateRequest request) {
        TicketCategoryResponse response = concertService.addTicketCategory(id, request);
        return ApiResponse.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all concerts", description = "Lists all concerts including DRAFT ones for operators")
    public ApiResponse<List<ConcertResponse>> getAllConcerts() {
        List<ConcertResponse> response = concertService.getAllConcerts();
        return ApiResponse.ok(response);
    }
}
