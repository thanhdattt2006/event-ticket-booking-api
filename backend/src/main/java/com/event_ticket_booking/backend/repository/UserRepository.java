package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Used for login/auth lookup — email has a unique index so this is always O(1)
    Optional<User> findByEmail(String email);
}
