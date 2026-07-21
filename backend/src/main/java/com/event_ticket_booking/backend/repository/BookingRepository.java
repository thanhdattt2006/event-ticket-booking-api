package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Idempotency check — called before every new booking creation (AGENTS.md §3)
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByBookingCode(String bookingCode);

    // User booking history — uses idx_user_history(user_id, created_at)
    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Operation dashboard filter — uses idx_status_created(status, created_at)
    Page<Booking> findByStatusOrderByCreatedAtDesc(Booking.Status status, Pageable pageable);

    /**
     * Atomic expiry — updates PENDING/AWAITING_PAYMENT bookings whose expires_at has passed.
     * Single SQL statement avoids the race condition where a payment callback arrives at the
     * exact moment the cronjob tries to expire the same booking (AGENTS.md §4).
     * Uses idx_cronjob_scan(status, expires_at) — do NOT remove that index.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.status = 'EXPIRED', b.updatedAt = :now " +
           "WHERE b.status IN ('PENDING', 'AWAITING_PAYMENT') AND b.expiresAt < :now")
    int expireBookings(@Param("now") LocalDateTime now);

    // Find the IDs of just-expired bookings so the revert service can process them
    @Query("SELECT b.id FROM Booking b WHERE b.status = 'EXPIRED' AND b.updatedAt >= :since")
    List<Long> findRecentlyExpiredIds(@Param("since") LocalDateTime since);
}
