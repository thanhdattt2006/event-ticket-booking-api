package com.event_ticket_booking.backend.controller;

import com.event_ticket_booking.backend.dto.request.BookingCreateRequest;
import com.event_ticket_booking.backend.dto.response.ApiResponse;
import com.event_ticket_booking.backend.dto.response.BookingResponse;
import com.event_ticket_booking.backend.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Customer booking APIs")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a booking", description = "Reserves tickets and applies voucher if any. Requires user ID in query param for testing context.")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @RequestParam Long userId,
            @Valid @RequestBody BookingCreateRequest request) {
        
        BookingResponse response = bookingService.createBooking(userId, request);
        
        // 4.2 Idempotency: return 200 instead of 201 if it's already existing.
        // We can detect if it's new by checking createdAt vs some logic, but for simplicity
        // returning 200 OK is fine for both, or we just return 200 OK always.
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{bookingCode}")
    @Operation(summary = "Get booking detail", description = "View booking detail and status by its unique booking code.")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingByCode(
            @PathVariable String bookingCode) {
        
        BookingResponse response = bookingService.getBookingByCode(bookingCode);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "Get user bookings", description = "Get a user's booking history.")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getUserBookings(
            @RequestParam Long userId,
            Pageable pageable) {
        
        Page<BookingResponse> response = bookingService.getUserBookings(userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
