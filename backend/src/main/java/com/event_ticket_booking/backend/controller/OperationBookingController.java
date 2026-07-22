package com.event_ticket_booking.backend.controller;

import com.event_ticket_booking.backend.dto.request.BookingStatusUpdateRequest;
import com.event_ticket_booking.backend.dto.response.ApiResponse;
import com.event_ticket_booking.backend.dto.response.OperationLogResponse;
import com.event_ticket_booking.backend.entity.Booking;
import com.event_ticket_booking.backend.service.OperationBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operation/bookings")
@RequiredArgsConstructor
public class OperationBookingController {

    private final OperationBookingService operationBookingService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Booking>>> getBookingsByStatus(
            @RequestParam Booking.Status status,
            Pageable pageable) {
        Page<Booking> bookings = operationBookingService.getBookingsByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateBookingStatus(
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusUpdateRequest request) {
        operationBookingService.updateBookingStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{id}/mark-suspicious")
    public ResponseEntity<ApiResponse<Void>> markSuspicious(
            @PathVariable Long id,
            @RequestParam Long operatorId) {
        operationBookingService.markSuspicious(id, operatorId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<List<OperationLogResponse>>> getBookingLogs(@PathVariable Long id) {
        List<OperationLogResponse> logs = operationBookingService.getBookingLogs(id);
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }
}
