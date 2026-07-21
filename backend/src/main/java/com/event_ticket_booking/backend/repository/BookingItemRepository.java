package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    // Fetch all items for a booking — used in revert to know which categories to release
    List<BookingItem> findByBookingId(Long bookingId);
}
