package com.event_ticket_booking.backend.repository;

import com.event_ticket_booking.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context load test — verifies all entity mappings are correct by running
 * against an in-memory H2 database (no Docker needed for this test).
 *
 * NOTE: H2 is added as test-scope dependency because @DataJpaTest replaces
 * the real datasource with an embedded one by default.
 *
 * This test does NOT cover concurrency — that's Phase 5 with Testcontainers.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"   // H2 dialect differs; let Hibernate generate from entities
})
class EntityMappingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Test
    void allRepositoriesLoadWithoutError() {
        // Spring context wired all 8 repositories successfully
        assertThat(userRepository).isNotNull();
        assertThat(concertRepository).isNotNull();
        assertThat(ticketCategoryRepository).isNotNull();
        assertThat(voucherRepository).isNotNull();
        assertThat(bookingRepository).isNotNull();
        assertThat(bookingItemRepository).isNotNull();
        assertThat(voucherRedemptionRepository).isNotNull();
        assertThat(operationLogRepository).isNotNull();
    }

    @Test
    void canPersistAndReloadUser() {
        var user = new User();
        user.setEmail("test@test.com");
        user.setPasswordHash("hashed");
        user.setFullName("Test User");
        user.setRole(User.Role.CUSTOMER);

        var saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();

        var found = userRepository.findByEmail("test@test.com");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Test User");
    }

    @Test
    void canPersistAndReloadConcertWithTicketCategory() {
        var operator = new User();
        operator.setEmail("op@test.com");
        operator.setPasswordHash("hash");
        operator.setFullName("Operator");
        operator.setRole(User.Role.OPERATOR);
        userRepository.save(operator);

        var concert = new Concert();
        concert.setName("Test Concert");
        concert.setVenue("Venue");
        concert.setEventDate(LocalDateTime.now().plusDays(30));
        concert.setSaleStartAt(LocalDateTime.now());
        concert.setSaleEndAt(LocalDateTime.now().plusDays(29));
        concert.setStatus(Concert.Status.PUBLISHED);
        concert.setCreatedBy(operator);
        concertRepository.save(concert);

        var category = new TicketCategory();
        category.setConcert(concert);
        category.setName("VIP");
        category.setPrice(new BigDecimal("2500000.00"));
        category.setQuantityTotal(100);
        category.setQuantitySold(0);
        ticketCategoryRepository.save(category);

        var categories = ticketCategoryRepository.findByConcertId(concert.getId());
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getPrice()).isEqualByComparingTo("2500000.00");
    }

    @Test
    void canPersistAndReloadVoucher() {
        var voucher = new Voucher();
        voucher.setCode("TESTCODE");
        voucher.setDiscountType(Voucher.DiscountType.PERCENTAGE);
        voucher.setDiscountValue(new BigDecimal("10.00"));
        voucher.setMaxUsage(100);
        voucher.setMaxUsagePerUser(1);
        voucher.setValidFrom(LocalDateTime.now());
        voucher.setValidUntil(LocalDateTime.now().plusDays(30));
        voucherRepository.save(voucher);

        var found = voucherRepository.findByCode("TESTCODE");
        assertThat(found).isPresent();
        assertThat(found.get().getUsedCount()).isZero();
    }
}
