package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    // Audit trail for a booking — uses idx_operation_logs_booking(booking_id)
    List<OperationLog> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
