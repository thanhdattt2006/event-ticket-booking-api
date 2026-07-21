package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConcertRepository extends JpaRepository<Concert, Long> {

    // Customer-facing: list only published concerts (uses idx_concerts_status_sale)
    List<Concert> findByStatus(Concert.Status status);
}
