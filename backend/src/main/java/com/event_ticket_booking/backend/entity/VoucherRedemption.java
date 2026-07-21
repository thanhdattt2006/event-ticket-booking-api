package com.event_ticket_booking.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Maps to voucher_redemptions table.
 * Source of truth for who has used which voucher and how many times.
 * When a booking is CANCELLED or EXPIRED, a revert transaction MUST:
 *   1. Set status = REVERTED, reverted_at = NOW() here
 *   2. Decrement vouchers.used_count
 * Both steps in the SAME @Transactional (AGENTS.md §2.4).
 */
@Getter
@Setter
@Entity
@Table(name = "voucher_redemptions")
public class VoucherRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.APPLIED;

    @Column(name = "redeemed_at", nullable = false, updatable = false)
    private LocalDateTime redeemedAt;

    @Column(name = "reverted_at")
    private LocalDateTime revertedAt;

    @PrePersist
    void prePersist() {
        redeemedAt = LocalDateTime.now();
    }

    public enum Status {
        APPLIED, REVERTED
    }
}
