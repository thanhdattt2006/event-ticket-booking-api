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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertServiceImplTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ConcertServiceImpl concertService;

    private User operator;
    private User customer;
    private Concert draftConcert;
    private TicketCategory ticketCategory;

    @BeforeEach
    void setUp() {
        operator = new User();
        operator.setId(1L);
        operator.setRole(User.Role.OPERATOR);

        customer = new User();
        customer.setId(2L);
        customer.setRole(User.Role.CUSTOMER);

        draftConcert = new Concert();
        draftConcert.setId(10L);
        draftConcert.setStatus(Concert.Status.DRAFT);
        draftConcert.setCreatedBy(operator);
        draftConcert.setName("Test Concert");

        ticketCategory = new TicketCategory();
        ticketCategory.setId(100L);
        ticketCategory.setConcert(draftConcert);
        ticketCategory.setPrice(new BigDecimal("50.0"));
        ticketCategory.setQuantityTotal(100);
        ticketCategory.setQuantitySold(0);
    }

    @Test
    void createConcert_Success() {
        ConcertCreateRequest req = new ConcertCreateRequest();
        req.setCreatedBy(1L);
        req.setName("New Concert");

        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(concertRepository.save(any(Concert.class))).thenAnswer(i -> {
            Concert c = i.getArgument(0);
            c.setId(11L);
            return c;
        });

        ConcertResponse res = concertService.createConcert(req);

        assertNotNull(res);
        assertEquals(Concert.Status.DRAFT, res.getStatus());
        assertEquals(11L, res.getId());
        verify(concertRepository).save(any(Concert.class));
    }

    @Test
    void createConcert_CustomerCannotCreate() {
        ConcertCreateRequest req = new ConcertCreateRequest();
        req.setCreatedBy(2L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(customer));

        assertThrows(BusinessException.class, () -> concertService.createConcert(req));
        verify(concertRepository, never()).save(any());
    }

    @Test
    void publishConcert_Success() {
        when(concertRepository.findById(10L)).thenReturn(Optional.of(draftConcert));
        when(ticketCategoryRepository.findByConcertId(10L)).thenReturn(List.of(ticketCategory));
        when(concertRepository.save(any(Concert.class))).thenAnswer(i -> i.getArgument(0));

        ConcertResponse res = concertService.publishConcert(10L);

        assertEquals(Concert.Status.PUBLISHED, res.getStatus());
        assertEquals(1, res.getTicketCategories().size());
    }

    @Test
    void publishConcert_FailsIfNoCategories() {
        when(concertRepository.findById(10L)).thenReturn(Optional.of(draftConcert));
        when(ticketCategoryRepository.findByConcertId(10L)).thenReturn(List.of()); // empty

        assertThrows(BusinessException.class, () -> concertService.publishConcert(10L));
    }

    @Test
    void addTicketCategory_Success() {
        TicketCategoryCreateRequest req = new TicketCategoryCreateRequest();
        req.setPrice(new BigDecimal("100"));
        req.setQuantityTotal(50);
        req.setName("VIP");

        when(concertRepository.findById(10L)).thenReturn(Optional.of(draftConcert));
        when(ticketCategoryRepository.save(any(TicketCategory.class))).thenAnswer(i -> {
            TicketCategory tc = i.getArgument(0);
            tc.setId(101L);
            return tc;
        });

        TicketCategoryResponse res = concertService.addTicketCategory(10L, req);

        assertNotNull(res);
        assertEquals(101L, res.getId());
        assertEquals(0, res.getQuantitySold());
    }

    @Test
    void addTicketCategory_FailsIfNotDraft() {
        draftConcert.setStatus(Concert.Status.PUBLISHED);
        when(concertRepository.findById(10L)).thenReturn(Optional.of(draftConcert));

        TicketCategoryCreateRequest req = new TicketCategoryCreateRequest();
        
        assertThrows(BusinessException.class, () -> concertService.addTicketCategory(10L, req));
        verify(ticketCategoryRepository, never()).save(any());
    }
}
