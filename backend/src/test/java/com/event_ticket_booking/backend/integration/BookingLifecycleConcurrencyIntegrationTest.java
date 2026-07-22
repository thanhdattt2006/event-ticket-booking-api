package com.event_ticket_booking.backend.integration;

import com.event_ticket_booking.backend.dto.request.BookingCreateRequest;
import com.event_ticket_booking.backend.dto.request.BookingItemRequest;
import com.event_ticket_booking.backend.dto.response.BookingResponse;
import com.event_ticket_booking.backend.entity.Booking;
import com.event_ticket_booking.backend.entity.Concert;
import com.event_ticket_booking.backend.entity.TicketCategory;
import com.event_ticket_booking.backend.entity.User;
import com.event_ticket_booking.backend.entity.Voucher;
import com.event_ticket_booking.backend.exception.BusinessException;
import com.event_ticket_booking.backend.repository.BookingRepository;
import com.event_ticket_booking.backend.repository.ConcertRepository;
import com.event_ticket_booking.backend.repository.TicketCategoryRepository;
import com.event_ticket_booking.backend.repository.UserRepository;
import com.event_ticket_booking.backend.repository.VoucherRepository;
import com.event_ticket_booking.backend.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest

@ActiveProfiles("test")
class BookingLifecycleConcurrencyIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void testRaceCondition_ConfirmPayment_Vs_ExpireRevert() throws InterruptedException {
        // 1. Setup Data with UUID to prevent state leakage
        User user = new User();
        user.setEmail("lifecycle_" + UUID.randomUUID().toString() + "@test.vn");
        user.setPasswordHash("hash");
        user.setFullName("Lifecycle Test User");
        user.setRole(User.Role.CUSTOMER);
        user = userRepository.save(user);

        // Create isolated concert, category and voucher so background cron jobs expiring old seed data don't affect our counts
        Concert concert = new Concert();
        concert.setName("Lifecycle Test Concert");
        concert.setVenue("Test Stadium");
        concert.setStatus(Concert.Status.PUBLISHED);
        concert.setCreatedBy(user);
        concert.setEventDate(LocalDateTime.now().plusDays(10));
        concert.setSaleStartAt(LocalDateTime.now().minusDays(1));
        concert.setSaleEndAt(LocalDateTime.now().plusDays(1));
        concert = concertRepository.save(concert);

        TicketCategory category = new TicketCategory();
        category.setConcert(concert);
        category.setName("VIP");
        category.setPrice(new java.math.BigDecimal("100.00"));
        category.setQuantityTotal(10);
        category.setQuantitySold(0);
        category = ticketCategoryRepository.save(category);

        Voucher voucher = new Voucher();
        voucher.setCode("LIFECYCLE_" + UUID.randomUUID().toString().substring(0, 8));
        voucher.setDiscountType(Voucher.DiscountType.FIXED_AMOUNT);
        voucher.setDiscountValue(new java.math.BigDecimal("10.00"));
        voucher.setValidFrom(LocalDateTime.now().minusDays(1));
        voucher.setValidUntil(LocalDateTime.now().plusDays(1));
        voucher.setMaxUsage(10);
        voucher.setMaxUsagePerUser(2);
        voucher.setUsedCount(0);
        voucher = voucherRepository.save(voucher);

        Long concertId = concert.getId();
        Long ticketCategoryId = category.getId(); 
        String voucherCode = voucher.getCode();

        TicketCategory categoryBefore = ticketCategoryRepository.findById(ticketCategoryId).orElseThrow();
        int initialQuantitySold = categoryBefore.getQuantitySold();

        Voucher voucherBefore = voucherRepository.findByCode(voucherCode).orElseThrow();
        int initialUsedCount = voucherBefore.getUsedCount();

        // Create the booking in PENDING state
        BookingCreateRequest req = new BookingCreateRequest();
        req.setConcertId(concertId);
        req.setIdempotencyKey(UUID.randomUUID().toString());
        req.setVoucherCode(voucherCode);
        BookingItemRequest item = new BookingItemRequest();
        item.setTicketCategoryId(ticketCategoryId);
        item.setQuantity(1);
        req.setItems(Collections.singletonList(item));

        BookingResponse response = bookingService.createBooking(user.getId(), req);
        Long bookingId = response.getId();
        String bookingCode = response.getBookingCode();

        // 2. Concurrency Setup
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<BusinessException> expectedExceptions = new CopyOnWriteArrayList<>();

        // Thread 1: Confirm Payment
        executor.submit(() -> {
            try {
                startLatch.await();
                bookingService.confirmPayment(bookingCode);
            } catch (BusinessException e) {
                expectedExceptions.add(e);
            } catch (Throwable t) {
                exceptions.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Expire Booking & Revert
        executor.submit(() -> {
            try {
                startLatch.await();
                bookingService.expireBooking(bookingId);
            } catch (BusinessException e) {
                expectedExceptions.add(e);
            } catch (Throwable t) {
                exceptions.add(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 3. Assertions
        assertTrue(exceptions.isEmpty(), "Unexpected non-business exceptions: " + exceptions);

        // Exactly one thread should have failed due to atomic update returning 0
        assertThat(expectedExceptions).hasSize(1);

        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        
        TicketCategory categoryAfter = ticketCategoryRepository.findById(ticketCategoryId).orElseThrow();
        Voucher voucherAfter = voucherRepository.findByCode(voucherCode).orElseThrow();

        if (booking.getStatus() == Booking.Status.PAID) {
            // Confirm payment won the race. Revert didn't happen.
            assertThat(categoryAfter.getQuantitySold()).isEqualTo(initialQuantitySold + 1);
            assertThat(voucherAfter.getUsedCount()).isEqualTo(initialUsedCount + 1);
        } else if (booking.getStatus() == Booking.Status.EXPIRED) {
            // Expire won the race. Revert happened.
            assertThat(categoryAfter.getQuantitySold()).isEqualTo(initialQuantitySold);
            assertThat(voucherAfter.getUsedCount()).isEqualTo(initialUsedCount);
        } else {
            throw new IllegalStateException("Booking must be either PAID or EXPIRED, found: " + booking.getStatus());
        }
    }
}
