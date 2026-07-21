package com.event_ticket_booking.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to ticket_categories table.
 * quantity_sold is NEVER updated via entity.save() — always via the atomic JPQL update
 * in TicketCategoryRepository.reserveTickets() to prevent race conditions (AGENTS.md §2.1).
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_categories")
public class TicketCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // BigDecimal — never use double/float for money (AGENTS.md §5)
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity_total", nullable = false)
    private Integer quantityTotal;

    // Read-only from JPA perspective — mutations go through the atomic UPDATE query
    @Column(name = "quantity_sold", nullable = false)
    private Integer quantitySold = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
