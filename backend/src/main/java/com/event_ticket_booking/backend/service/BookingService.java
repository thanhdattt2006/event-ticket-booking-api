package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.BookingCreateRequest;
import com.event_ticket_booking.backend.dto.response.BookingResponse;

public interface BookingService {
    BookingResponse createBooking(Long userId, BookingCreateRequest request);
}
