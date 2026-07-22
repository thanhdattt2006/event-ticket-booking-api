package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.BookingStatusUpdateRequest;
import com.event_ticket_booking.backend.dto.response.OperationLogResponse;
import com.event_ticket_booking.backend.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OperationBookingService {
    Page<Booking> getBookingsByStatus(Booking.Status status, Pageable pageable);
    void updateBookingStatus(Long bookingId, BookingStatusUpdateRequest request);
    void markSuspicious(Long bookingId, Long operatorId);
    List<OperationLogResponse> getBookingLogs(Long bookingId);
}
