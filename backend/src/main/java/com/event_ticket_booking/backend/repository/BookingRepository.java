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

    @Modifying
    @Query("UPDATE Booking b SET b.status = 'PAID', b.updatedAt = :now WHERE b.bookingCode = :code AND b.status = 'PENDING'")
    int confirmPayment(@Param("code") String code, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Booking b SET b.status = 'EXPIRED', b.updatedAt = :now WHERE b.id = :id AND b.status = 'PENDING'")
    int updateStatusToExpired(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Booking b SET b.status = 'CANCELLED', b.updatedAt = :now WHERE b.bookingCode = :code AND b.status = 'PENDING'")
    int cancelBooking(@Param("code") String code, @Param("now") LocalDateTime now);

    // Find pending bookings that have expired
    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.expiresAt < :now")
    List<Booking> findExpiredPendingBookings(@Param("now") LocalDateTime now);
}
