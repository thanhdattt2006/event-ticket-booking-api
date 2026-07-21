package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.BookingCreateRequest;
import com.event_ticket_booking.backend.dto.response.BookingResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingService {
    BookingResponse createBooking(Long userId, BookingCreateRequest request);
    BookingResponse getBookingByCode(String bookingCode);
    Page<BookingResponse> getUserBookings(Long userId, Pageable pageable);
}
