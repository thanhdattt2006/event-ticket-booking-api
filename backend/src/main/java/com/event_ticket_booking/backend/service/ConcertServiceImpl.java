package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.ConcertCreateRequest;
import com.event_ticket_booking.backend.dto.request.TicketCategoryCreateRequest;
import com.event_ticket_booking.backend.dto.response.ConcertResponse;
import com.event_ticket_booking.backend.dto.response.TicketCategoryResponse;
import com.event_ticket_booking.backend.entity.Concert;
import com.event_ticket_booking.backend.entity.TicketCategory;
import com.event_ticket_booking.backend.entity.User;
import com.event_ticket_booking.backend.exception.BusinessException;
import com.event_ticket_booking.backend.exception.ResourceNotFoundException;
import com.event_ticket_booking.backend.repository.ConcertRepository;
import com.event_ticket_booking.backend.repository.TicketCategoryRepository;
import com.event_ticket_booking.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConcertServiceImpl implements ConcertService {

    private final ConcertRepository concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ConcertResponse createConcert(ConcertCreateRequest request) {
        User operator = userRepository.findById(request.getCreatedBy())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getCreatedBy()));

        // Simple check to ensure only OPERATOR or ADMIN can create, but auth is not fully implemented per scope
        if (operator.getRole() == User.Role.CUSTOMER) {
            throw new BusinessException("Customers cannot create concerts");
        }

        Concert concert = new Concert();
        concert.setName(request.getName());
        concert.setDescription(request.getDescription());
        concert.setVenue(request.getVenue());
        concert.setEventDate(request.getEventDate());
        concert.setSaleStartAt(request.getSaleStartAt());
        concert.setSaleEndAt(request.getSaleEndAt());
        concert.setCreatedBy(operator);
        concert.setStatus(Concert.Status.DRAFT);

        concert = concertRepository.save(concert);
        return mapToConcertResponse(concert, List.of());
    }

    @Override
    @Transactional
    public ConcertResponse publishConcert(Long id) {
        Concert concert = concertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Concert not found with id: " + id));

        if (concert.getStatus() != Concert.Status.DRAFT) {
            throw new BusinessException("Only DRAFT concerts can be published");
        }

        // Verify if it has at least one ticket category before publishing? Optional, but good practice.
        List<TicketCategory> categories = ticketCategoryRepository.findByConcertId(id);
        if (categories.isEmpty()) {
            throw new BusinessException("Cannot publish concert without ticket categories");
        }

        concert.setStatus(Concert.Status.PUBLISHED);
        concert = concertRepository.save(concert);

        return mapToConcertResponse(concert, categories);
    }

    @Override
    @Transactional
    public TicketCategoryResponse addTicketCategory(Long concertId, TicketCategoryCreateRequest request) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ResourceNotFoundException("Concert not found with id: " + concertId));

        if (concert.getStatus() != Concert.Status.DRAFT) {
            throw new BusinessException("Can only add categories to DRAFT concerts");
        }

        TicketCategory category = new TicketCategory();
        category.setConcert(concert);
        category.setName(request.getName());
        category.setPrice(request.getPrice());
        category.setQuantityTotal(request.getQuantityTotal());
        // quantitySold defaults to 0 from entity

        category = ticketCategoryRepository.save(category);

        return mapToTicketCategoryResponse(category);
    }

    @Override
    public List<ConcertResponse> getAllConcerts() {
        return concertRepository.findAll().stream()
                .map(concert -> {
                    List<TicketCategory> categories = ticketCategoryRepository.findByConcertId(concert.getId());
                    return mapToConcertResponse(concert, categories);
                })
                .collect(Collectors.toList());
    }

    private ConcertResponse mapToConcertResponse(Concert concert, List<TicketCategory> categories) {
        return ConcertResponse.builder()
                .id(concert.getId())
                .name(concert.getName())
                .description(concert.getDescription())
                .venue(concert.getVenue())
                .eventDate(concert.getEventDate())
                .saleStartAt(concert.getSaleStartAt())
                .saleEndAt(concert.getSaleEndAt())
                .status(concert.getStatus())
                .createdBy(concert.getCreatedBy().getId())
                .ticketCategories(categories.stream().map(this::mapToTicketCategoryResponse).collect(Collectors.toList()))
                .build();
    }

    private TicketCategoryResponse mapToTicketCategoryResponse(TicketCategory category) {
        return TicketCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .price(category.getPrice())
                .quantityTotal(category.getQuantityTotal())
                .quantitySold(category.getQuantitySold())
                .build();
    }
}
