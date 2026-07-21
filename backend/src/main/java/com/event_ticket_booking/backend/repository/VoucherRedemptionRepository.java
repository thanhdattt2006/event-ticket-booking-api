package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {

    /**
     * Count how many times this user has APPLIED this voucher.
     * Called while holding the pessimistic lock on the voucher row (AGENTS.md §2.2).
     * Uses idx_voucher_redemptions_check(voucher_id, user_id, status).
     *
     * We count only APPLIED (not REVERTED) — a reverted redemption does not count
     * toward the per-user limit because the booking was cancelled/expired.
     */
    @Query("SELECT COUNT(vr) FROM VoucherRedemption vr " +
           "WHERE vr.voucher.id = :voucherId AND vr.user.id = :userId AND vr.status = 'APPLIED'")
    long countAppliedByVoucherAndUser(@Param("voucherId") Long voucherId, @Param("userId") Long userId);

    // Find the APPLIED redemption for a booking — needed during the revert step
    Optional<VoucherRedemption> findByBookingIdAndStatus(Long bookingId, VoucherRedemption.Status status);
}
