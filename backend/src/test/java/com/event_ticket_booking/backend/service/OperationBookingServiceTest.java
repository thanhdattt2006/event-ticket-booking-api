package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.BookingStatusUpdateRequest;
import com.event_ticket_booking.backend.entity.Booking;
import com.event_ticket_booking.backend.entity.OperationLog;
import com.event_ticket_booking.backend.entity.User;
import com.event_ticket_booking.backend.exception.ResourceNotFoundException;
import com.event_ticket_booking.backend.repository.BookingRepository;
import com.event_ticket_booking.backend.repository.OperationLogRepository;
import com.event_ticket_booking.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationBookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OperationBookingServiceImpl operationBookingService;

    @Test
    void testUpdateBookingStatus_Success() {
        Long bookingId = 1L;
        Long operatorId = 2L;
        
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setStatus(Booking.Status.PENDING);
        
        User operator = new User();
        operator.setId(operatorId);
        
        BookingStatusUpdateRequest request = new BookingStatusUpdateRequest();
        request.setNewStatus(Booking.Status.CANCELLED);
        request.setReason("Customer requested");
        request.setOperatorId(operatorId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(userRepository.findById(operatorId)).thenReturn(Optional.of(operator));

        operationBookingService.updateBookingStatus(bookingId, request);

        assertThat(booking.getStatus()).isEqualTo(Booking.Status.CANCELLED);
        
        verify(operationLogRepository).save(argThat(log -> 
            log.getAction().equals("CHANGE_STATUS") &&
            log.getOldValue().equals("PENDING") &&
            log.getNewValue().equals("CANCELLED") &&
            log.getNote().equals("Customer requested") &&
            log.getOperator().getId().equals(operatorId) &&
            log.getBooking().getId().equals(bookingId)
        ));
        verify(bookingRepository).save(booking);
    }

    @Test
    void testMarkSuspicious_Success() {
        Long bookingId = 1L;
        Long operatorId = 2L;
        
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setSuspicious(false);
        
        User operator = new User();
        operator.setId(operatorId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(userRepository.findById(operatorId)).thenReturn(Optional.of(operator));

        operationBookingService.markSuspicious(bookingId, operatorId);

        assertThat(booking.isSuspicious()).isTrue();
        
        verify(operationLogRepository).save(argThat(log -> 
            log.getAction().equals("MARK_SUSPICIOUS") &&
            log.getOldValue().equals("false") &&
            log.getNewValue().equals("true") &&
            log.getNote().equals("Flagged by operator")
        ));
        verify(bookingRepository).save(booking);
    }
    
    @Test
    void testUpdateBookingStatus_BookingNotFound() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.empty());
        
        BookingStatusUpdateRequest request = new BookingStatusUpdateRequest();
        request.setNewStatus(Booking.Status.CANCELLED);
        request.setOperatorId(2L);
        
        assertThatThrownBy(() -> operationBookingService.updateBookingStatus(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");
    }
}
