package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.response.VoucherValidationResponse;
import com.event_ticket_booking.backend.entity.Voucher;
import com.event_ticket_booking.backend.repository.VoucherRedemptionRepository;
import com.event_ticket_booking.backend.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;

    @Override
    public VoucherValidationResponse validateVoucher(String code, Long userId, Long concertId) {
        Voucher voucher = voucherRepository.findByCode(code).orElse(null);
        if (voucher == null) {
            return VoucherValidationResponse.builder()
                    .valid(false)
                    .message("Voucher not found")
                    .code(code)
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getValidFrom()) || now.isAfter(voucher.getValidUntil())) {
            return buildErrorResponse(voucher, "Voucher is not within its validity period");
        }

        if (voucher.getUsedCount() >= voucher.getMaxUsage()) {
            return buildErrorResponse(voucher, "Voucher quota exceeded");
        }

        if (voucher.getConcert() != null && !voucher.getConcert().getId().equals(concertId)) {
            return buildErrorResponse(voucher, "Voucher is not applicable for this concert");
        }

        if (userId != null) {
            long userUsage = voucherRedemptionRepository.countAppliedByVoucherAndUser(voucher.getId(), userId);
            if (userUsage >= voucher.getMaxUsagePerUser()) {
                return buildErrorResponse(voucher, "User has exceeded the usage limit for this voucher");
            }
        }

        return VoucherValidationResponse.builder()
                .valid(true)
                .message("Voucher is valid")
                .code(voucher.getCode())
                .discountType(voucher.getDiscountType().name())
                .discountValue(voucher.getDiscountValue())
                .build();
    }

    private VoucherValidationResponse buildErrorResponse(Voucher voucher, String message) {
        return VoucherValidationResponse.builder()
                .valid(false)
                .message(message)
                .code(voucher.getCode())
                .discountType(voucher.getDiscountType().name())
                .discountValue(voucher.getDiscountValue())
                .build();
    }
}
