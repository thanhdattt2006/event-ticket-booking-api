package com.event_ticket_booking.backend.scheduler;

import com.event_ticket_booking.backend.entity.Booking;
import com.event_ticket_booking.backend.repository.BookingRepository;
import com.event_ticket_booking.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCronJob {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    /**
     * Runs every minute to find expired bookings and process them one by one.
     * The processing is atomic to avoid race conditions with payment confirmation.
     */
    @Scheduled(fixedRateString = "${app.cron.expire-booking-rate:60000}")
    public void expireBookings() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredBookings = bookingRepository.findExpiredPendingBookings(now);
        
        for (Booking booking : expiredBookings) {
            try {
                bookingService.expireBooking(booking.getId());
                log.info("Successfully expired booking: {}", booking.getId());
            } catch (Exception e) {
                log.error("Failed to expire booking: {}", booking.getId(), e);
            }
        }
    }
}
