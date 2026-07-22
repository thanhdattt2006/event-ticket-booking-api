package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.BookingStatusUpdateRequest;
import com.event_ticket_booking.backend.dto.response.OperationLogResponse;
import com.event_ticket_booking.backend.entity.Booking;
import com.event_ticket_booking.backend.entity.OperationLog;
import com.event_ticket_booking.backend.entity.User;
import com.event_ticket_booking.backend.exception.ResourceNotFoundException;
import com.event_ticket_booking.backend.repository.BookingRepository;
import com.event_ticket_booking.backend.repository.OperationLogRepository;
import com.event_ticket_booking.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationBookingServiceImpl implements OperationBookingService {

    private final BookingRepository bookingRepository;
    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;

    @Override
    public Page<Booking> getBookingsByStatus(Booking.Status status, Pageable pageable) {
        return bookingRepository.findByStatus(status, pageable);
    }

    @Override
    @Transactional
    public void updateBookingStatus(Long bookingId, BookingStatusUpdateRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        
        User operator = userRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        OperationLog log = new OperationLog();
        log.setBooking(booking);
        log.setOperator(operator);
        log.setAction("CHANGE_STATUS");
        log.setOldValue(booking.getStatus().name());
        log.setNewValue(request.getNewStatus().name());
        log.setNote(request.getReason());
        operationLogRepository.save(log);

        booking.setStatus(request.getNewStatus());
        bookingRepository.save(booking);
    }

    @Override
    @Transactional
    public void markSuspicious(Long bookingId, Long operatorId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        OperationLog log = new OperationLog();
        log.setBooking(booking);
        log.setOperator(operator);
        log.setAction("MARK_SUSPICIOUS");
        log.setOldValue("false");
        log.setNewValue("true");
        log.setNote("Flagged by operator");
        operationLogRepository.save(log);

        booking.setSuspicious(true);
        bookingRepository.save(booking);
    }

    @Override
    public List<OperationLogResponse> getBookingLogs(Long bookingId) {
        return operationLogRepository.findByBookingIdOrderByCreatedAtAsc(bookingId)
                .stream()
                .map(OperationLogResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
