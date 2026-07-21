package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    // Read-only lookup for validation display (no lock — Phase 3 validate API)
    Optional<Voucher> findByCode(String code);

    /**
     * Pessimistic write lock on the voucher row.
     * MUST be the first thing acquired inside the voucher-application transaction,
     * BEFORE reading voucher_redemptions — this is what prevents two concurrent
     * requests from both passing the max_usage_per_user check (AGENTS.md §2.2).
     *
     * Translates to: SELECT ... FROM vouchers WHERE code = ? FOR UPDATE
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.code = :code")
    Optional<Voucher> findByCodeForUpdate(@Param("code") String code);

    /**
     * Atomic used_count increment — called after all validations pass (while still
     * holding the pessimistic lock from findByCodeForUpdate).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount + 1 WHERE v.id = :id")
    void incrementUsedCount(@Param("id") Long id);

    /**
     * Atomic used_count decrement — part of the 3-step revert in AGENTS.md §2.4.
     * Must be called in the same @Transactional as the ticket release and redemption
     * status update.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount - 1 WHERE v.id = :id AND v.usedCount > 0")
    void decrementUsedCount(@Param("id") Long id);
}
