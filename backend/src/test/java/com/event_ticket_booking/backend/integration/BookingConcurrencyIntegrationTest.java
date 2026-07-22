package com.event_ticket_booking.backend.integration;

import com.event_ticket_booking.backend.TestcontainersConfiguration;
import com.event_ticket_booking.backend.dto.request.BookingCreateRequest;
import com.event_ticket_booking.backend.dto.request.BookingItemRequest;
import com.event_ticket_booking.backend.entity.Booking;
import com.event_ticket_booking.backend.entity.TicketCategory;
import com.event_ticket_booking.backend.entity.User;
import com.event_ticket_booking.backend.exception.BusinessException;
import com.event_ticket_booking.backend.repository.BookingRepository;
import com.event_ticket_booking.backend.repository.TicketCategoryRepository;
import com.event_ticket_booking.backend.repository.UserRepository;
import com.event_ticket_booking.backend.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
// @Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BookingConcurrencyIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testOversell_preventedByAtomicUpdate() throws InterruptedException {
        // Data reset step to fix state leakage between tests
        TicketCategory targetCategory = ticketCategoryRepository.findById(5L).orElseThrow();
        targetCategory.setQuantityTotal(5);
        targetCategory.setQuantitySold(0);
        ticketCategoryRepository.save(targetCategory);

        // 5.2 "Oversell" test
        // Concert 3 has Category 5 with 5 VIP tickets (seeded)
        Long ticketCategoryId = 5L;
        Long concertId = 3L;
        Long userId = 3L;
        int numberOfThreads = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    BookingCreateRequest req = new BookingCreateRequest();
                    req.setConcertId(concertId);
                    req.setIdempotencyKey(UUID.randomUUID().toString());
                    BookingItemRequest item = new BookingItemRequest();
                    item.setTicketCategoryId(ticketCategoryId);
                    item.setQuantity(1);
                    req.setItems(Collections.singletonList(item));

                    bookingService.createBooking(userId, req);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // Expected - InsufficientTicketException extends BusinessException
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Unleash all threads at once
        doneLatch.await();
        executor.shutdown();

        assertTrue(exceptions.isEmpty(), "Unexpected exceptions: " + exceptions);

        TicketCategory category = ticketCategoryRepository.findById(ticketCategoryId).orElseThrow();
        assertThat(category.getQuantitySold()).isLessThanOrEqualTo(category.getQuantityTotal());
        assertThat(category.getQuantitySold()).isEqualTo(5);
        assertThat(successCount.get()).isEqualTo(5);
    }

    @Test
    void testDuplicateIdempotencyKey_onlyOneBookingCreated() throws InterruptedException {
        // 5.3 "Duplicate idempotency" test
        Long concertId = 1L;
        Long userId = 3L;
        Long ticketCategoryId = 1L; // Has plenty of tickets
        String idempotencyKey = UUID.randomUUID().toString();
        int numberOfThreads = 5;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    BookingCreateRequest req = new BookingCreateRequest();
                    req.setConcertId(concertId);
                    req.setIdempotencyKey(idempotencyKey);
                    BookingItemRequest item = new BookingItemRequest();
                    item.setTicketCategoryId(ticketCategoryId);
                    item.setQuantity(1);
                    req.setItems(Collections.singletonList(item));

                    bookingService.createBooking(userId, req);
                } catch (DataIntegrityViolationException e) {
                    // Expected if DB unique constraint kicks in (if idempotency check has a race
                    // condition)
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertTrue(exceptions.isEmpty(), "Unexpected exceptions: " + exceptions);

        List<Booking> bookings = bookingRepository.findAll();
        long count = bookings.stream().filter(b -> idempotencyKey.equals(b.getIdempotencyKey())).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testVoucherAbuse_preventedByPessimisticLock() throws InterruptedException {
        // 5.4 "Voucher abuse" test - one user attempts to exceed max_usage_per_user
        // Voucher 6 (MULTI_USE) has max_usage_per_user = 3
        Long concertId = 1L;
        Long userId = 3L;
        Long ticketCategoryId = 2L;
        String voucherCode = "MULTI_USE";
        int numberOfThreads = 10;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    BookingCreateRequest req = new BookingCreateRequest();
                    req.setConcertId(concertId);
                    req.setIdempotencyKey(UUID.randomUUID().toString());
                    req.setVoucherCode(voucherCode);
                    BookingItemRequest item = new BookingItemRequest();
                    item.setTicketCategoryId(ticketCategoryId);
                    item.setQuantity(1);
                    req.setItems(Collections.singletonList(item));

                    bookingService.createBooking(userId, req);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // Expecting BusinessException for quota exceeded
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertTrue(exceptions.isEmpty(), "Unexpected exceptions: " + exceptions);
        assertThat(successCount.get()).isLessThanOrEqualTo(3);
    }

    @Test
    void testVoucherExhaustion_preventedByPessimisticLock() throws InterruptedException {
        // 5.5 "Voucher system-wide exhaustion" test
        // Voucher 3 (LIMITED3) has max_usage = 3
        Long concertId = 1L;
        Long ticketCategoryId = 3L;
        String voucherCode = "LIMITED3";
        int numberOfThreads = 10;

        // Ensure we have enough users to bypass max_usage_per_user (which is 1)
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            User user = new User();
            user.setEmail("tempuser_" + java.util.UUID.randomUUID().toString() + "@test.vn");
            user.setPasswordHash("hash");
            user.setFullName("Temp User " + i);
            user.setRole(User.Role.CUSTOMER);
            user = userRepository.save(user);
            userIds.add(user.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final Long uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    BookingCreateRequest req = new BookingCreateRequest();
                    req.setConcertId(concertId);
                    req.setIdempotencyKey(UUID.randomUUID().toString());
                    req.setVoucherCode(voucherCode);
                    BookingItemRequest item = new BookingItemRequest();
                    item.setTicketCategoryId(ticketCategoryId);
                    item.setQuantity(1);
                    req.setItems(Collections.singletonList(item));

                    bookingService.createBooking(uid, req);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    // Expecting BusinessException for quota exceeded
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertTrue(exceptions.isEmpty(), "Unexpected exceptions: " + exceptions);
        assertThat(successCount.get()).isLessThanOrEqualTo(3);
    }
}
