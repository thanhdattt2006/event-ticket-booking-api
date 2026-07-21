package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.ConcertCreateRequest;
import com.event_ticket_booking.backend.dto.request.TicketCategoryCreateRequest;
import com.event_ticket_booking.backend.dto.response.ConcertResponse;
import com.event_ticket_booking.backend.dto.response.TicketCategoryResponse;

import java.util.List;

public interface ConcertService {
    ConcertResponse createConcert(ConcertCreateRequest request);
    ConcertResponse publishConcert(Long id);
    TicketCategoryResponse addTicketCategory(Long concertId, TicketCategoryCreateRequest request);
    List<ConcertResponse> getAllConcerts();
}
