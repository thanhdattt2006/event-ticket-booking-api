package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.request.BookingCreateRequest;
import com.event_ticket_booking.backend.dto.request.BookingItemRequest;
import com.event_ticket_booking.backend.dto.response.BookingResponse;
import com.event_ticket_booking.backend.entity.*;
import com.event_ticket_booking.backend.exception.BusinessException;
import com.event_ticket_booking.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private ConcertRepository concertRepository;
    @Mock
    private TicketCategoryRepository ticketCategoryRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private VoucherRedemptionRepository voucherRedemptionRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User user;
    private Concert concert;
    private TicketCategory category1;
    private TicketCategory category2;
    private BookingCreateRequest request;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        concert = new Concert();
        concert.setId(100L);
        concert.setStatus(Concert.Status.PUBLISHED);
        concert.setSaleStartAt(LocalDateTime.now().minusDays(1));
        concert.setSaleEndAt(LocalDateTime.now().plusDays(1));

        category1 = new TicketCategory();
        category1.setId(10L);
        category1.setConcert(concert);
        category1.setPrice(new BigDecimal("100"));

        category2 = new TicketCategory();
        category2.setId(20L);
        category2.setConcert(concert);
        category2.setPrice(new BigDecimal("200"));

        request = new BookingCreateRequest();
        request.setIdempotencyKey("uuid-123");
        request.setConcertId(100L);

        BookingItemRequest item1 = new BookingItemRequest();
        item1.setTicketCategoryId(20L);
        item1.setQuantity(2);

        BookingItemRequest item2 = new BookingItemRequest();
        item2.setTicketCategoryId(10L);
        item2.setQuantity(1);

        // Note: added in reverse order (20 then 10) to test sorting (AGENTS.md 2.3)
        request.setItems(List.of(item1, item2));
    }

    @Test
    void createBooking_IdempotencyReturnsExisting() {
        Booking existing = new Booking();
        existing.setId(999L);
        existing.setIdempotencyKey("uuid-123");

        when(bookingRepository.findByIdempotencyKey("uuid-123")).thenReturn(Optional.of(existing));

        BookingResponse response = bookingService.createBooking(1L, request);

        assertEquals(999L, response.getId());
        verify(concertRepository, never()).findById(any()); // flow stops early
    }

    @Test
    void createBooking_InvalidConcertStatus() {
        when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        concert.setStatus(Concert.Status.DRAFT);
        when(concertRepository.findById(100L)).thenReturn(Optional.of(concert));

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(1L, request));
        assertEquals("Concert is not published", ex.getMessage());
    }

    @Test
    void createBooking_AtomicReserveFails() {
        when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(concertRepository.findById(100L)).thenReturn(Optional.of(concert));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(ticketCategoryRepository.findById(10L)).thenReturn(Optional.of(category1));
        // Reserve returns 0 rows affected (sold out)
        when(ticketCategoryRepository.reserveTickets(10L, 1)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> bookingService.createBooking(1L, request));
        assertTrue(ex.getMessage().contains("sold out"));
    }

    @Test
    void createBooking_SuccessWithoutVoucher() {
        when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(concertRepository.findById(100L)).thenReturn(Optional.of(concert));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(ticketCategoryRepository.findById(10L)).thenReturn(Optional.of(category1));
        when(ticketCategoryRepository.findById(20L)).thenReturn(Optional.of(category2));

        when(ticketCategoryRepository.reserveTickets(10L, 1)).thenReturn(1);
        when(ticketCategoryRepository.reserveTickets(20L, 2)).thenReturn(1);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(1000L);
            return b;
        });

        BookingResponse response = bookingService.createBooking(1L, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("500"), response.getTotalAmount()); // 1*100 + 2*200
        assertEquals(new BigDecimal("0"), response.getDiscountAmount());

        // Verify order of reservation (10L first, then 20L)
        org.mockito.InOrder inOrder = inOrder(ticketCategoryRepository);
        inOrder.verify(ticketCategoryRepository).reserveTickets(10L, 1);
        inOrder.verify(ticketCategoryRepository).reserveTickets(20L, 2);
    }

    @Test
    void createBooking_SuccessWithVoucher() {
        request.setVoucherCode("SAVE50");

        when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(concertRepository.findById(100L)).thenReturn(Optional.of(concert));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(ticketCategoryRepository.findById(10L)).thenReturn(Optional.of(category1));
        when(ticketCategoryRepository.findById(20L)).thenReturn(Optional.of(category2));
        when(ticketCategoryRepository.reserveTickets(any(), anyInt())).thenReturn(1);

        Voucher voucher = new Voucher();
        voucher.setId(5L);
        voucher.setCode("SAVE50");
        voucher.setDiscountType(Voucher.DiscountType.FIXED_AMOUNT);
        voucher.setDiscountValue(new BigDecimal("50"));
        voucher.setMaxUsage(100);
        voucher.setUsedCount(0);
        voucher.setMaxUsagePerUser(1);
        voucher.setValidFrom(LocalDateTime.now().minusDays(1));
        voucher.setValidUntil(LocalDateTime.now().plusDays(1));

        when(voucherRepository.findByCodeForUpdate("SAVE50")).thenReturn(Optional.of(voucher));
        when(voucherRedemptionRepository.countAppliedByVoucherAndUser(5L, 1L)).thenReturn(0L);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(1000L);
            return b;
        });

        BookingResponse response = bookingService.createBooking(1L, request);

        assertEquals(new BigDecimal("500"), response.getTotalAmount());
        assertEquals(new BigDecimal("50"), response.getDiscountAmount());

        verify(voucherRepository).incrementUsedCount(5L);
        verify(voucherRedemptionRepository).save(any(VoucherRedemption.class));
    }

    @Test
    void getBookingByCode_Success() {
        Booking booking = new Booking();
        booking.setId(100L);
        booking.setBookingCode("ABCDEFGH");
        booking.setStatus(Booking.Status.PENDING);
        booking.setReservedAt(LocalDateTime.now());

        when(bookingRepository.findByBookingCode("ABCDEFGH")).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.getBookingByCode("ABCDEFGH");

        assertEquals(100L, response.getId());
        assertEquals("ABCDEFGH", response.getBookingCode());
    }

    @Test
    void getUserBookings_Success() {
        Booking booking = new Booking();
        booking.setId(100L);
        booking.setBookingCode("XYZ");
        booking.setStatus(Booking.Status.PENDING);

        Page<Booking> page = new PageImpl<>(List.of(booking));
        Pageable pageable = PageRequest.of(0, 10);

        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page);

        Page<BookingResponse> response = bookingService.getUserBookings(1L, pageable);

        assertEquals(1, response.getTotalElements());
        assertEquals("XYZ", response.getContent().get(0).getBookingCode());
    }
}
