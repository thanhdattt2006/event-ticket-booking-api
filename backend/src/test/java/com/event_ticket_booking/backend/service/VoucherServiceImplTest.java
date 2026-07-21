package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.response.VoucherValidationResponse;
import com.event_ticket_booking.backend.entity.Concert;
import com.event_ticket_booking.backend.entity.Voucher;
import com.event_ticket_booking.backend.repository.VoucherRedemptionRepository;
import com.event_ticket_booking.backend.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceImplTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @InjectMocks
    private VoucherServiceImpl voucherService;

    private Voucher validVoucher;

    @BeforeEach
    void setUp() {
        validVoucher = new Voucher();
        validVoucher.setId(1L);
        validVoucher.setCode("VALID10");
        validVoucher.setDiscountType(Voucher.DiscountType.PERCENTAGE);
        validVoucher.setDiscountValue(new BigDecimal("10.0"));
        validVoucher.setMaxUsage(100);
        validVoucher.setUsedCount(10);
        validVoucher.setMaxUsagePerUser(2);
        validVoucher.setValidFrom(LocalDateTime.now().minusDays(1));
        validVoucher.setValidUntil(LocalDateTime.now().plusDays(10));
    }

    @Test
    void validateVoucher_NotFound() {
        when(voucherRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        VoucherValidationResponse response = voucherService.validateVoucher("INVALID", 1L, null);

        assertFalse(response.isValid());
        assertEquals("Voucher not found", response.getMessage());
    }

    @Test
    void validateVoucher_Expired() {
        validVoucher.setValidUntil(LocalDateTime.now().minusDays(1));
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(validVoucher));

        VoucherValidationResponse response = voucherService.validateVoucher("VALID10", 1L, null);

        assertFalse(response.isValid());
        assertEquals("Voucher is not within its validity period", response.getMessage());
    }

    @Test
    void validateVoucher_NotYetValid() {
        validVoucher.setValidFrom(LocalDateTime.now().plusDays(1));
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(validVoucher));

        VoucherValidationResponse response = voucherService.validateVoucher("VALID10", 1L, null);

        assertFalse(response.isValid());
        assertEquals("Voucher is not within its validity period", response.getMessage());
    }

    @Test
    void validateVoucher_QuotaExceeded() {
        validVoucher.setUsedCount(100); // matches maxUsage
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(validVoucher));

        VoucherValidationResponse response = voucherService.validateVoucher("VALID10", 1L, null);

        assertFalse(response.isValid());
        assertEquals("Voucher quota exceeded", response.getMessage());
    }

    @Test
    void validateVoucher_ConcertMismatch() {
        Concert concert = new Concert();
        concert.setId(2L);
        validVoucher.setConcert(concert);
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(validVoucher));

        VoucherValidationResponse response = voucherService.validateVoucher("VALID10", 1L, 3L);

        assertFalse(response.isValid());
        assertEquals("Voucher is not applicable for this concert", response.getMessage());
    }

    @Test
    void validateVoucher_UserLimitExceeded() {
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(validVoucher));
        when(voucherRedemptionRepository.countAppliedByVoucherAndUser(1L, 1L)).thenReturn(2L); // user used 2 times (max is 2)

        VoucherValidationResponse response = voucherService.validateVoucher("VALID10", 1L, null);

        assertFalse(response.isValid());
        assertEquals("User has exceeded the usage limit for this voucher", response.getMessage());
    }

    @Test
    void validateVoucher_Success() {
        when(voucherRepository.findByCode("VALID10")).thenReturn(Optional.of(validVoucher));
        when(voucherRedemptionRepository.countAppliedByVoucherAndUser(1L, 1L)).thenReturn(1L);

        VoucherValidationResponse response = voucherService.validateVoucher("VALID10", 1L, null);

        assertTrue(response.isValid());
        assertEquals("Voucher is valid", response.getMessage());
        assertEquals("PERCENTAGE", response.getDiscountType());
    }
}
