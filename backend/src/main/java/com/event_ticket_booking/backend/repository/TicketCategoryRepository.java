package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.TicketCategory;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findByConcertId(Long concertId);

    /**
     * Atomic inventory deduction — single SQL UPDATE with a WHERE guard.
     * InnoDB row-level lock serialises concurrent updates on the same row.
     * Returns the number of affected rows:
     *   1 → reservation succeeded
     *   0 → sold out (quantity_total - quantity_sold < qty) — caller must throw
     *
     * NEVER replace this with a read-then-save pattern: that introduces a race condition
     * between the read and the write (AGENTS.md §2.1).
     */
    @Modifying
    @Transactional
    @Query("UPDATE TicketCategory t SET t.quantitySold = t.quantitySold + :qty " +
           "WHERE t.id = :id AND (t.quantityTotal - t.quantitySold) >= :qty")
    int reserveTickets(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomic inventory release on booking CANCELLED/EXPIRED.
     * Must be called inside the same @Transactional as the voucher revert (AGENTS.md §2.4).
     */
    @Modifying
    @Transactional
    @Query("UPDATE TicketCategory t SET t.quantitySold = t.quantitySold - :qty " +
           "WHERE t.id = :id AND t.quantitySold >= :qty")
    int releaseTickets(@Param("id") Long id, @Param("qty") int qty);
}
