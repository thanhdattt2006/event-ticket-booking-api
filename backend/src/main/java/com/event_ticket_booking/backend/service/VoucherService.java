package com.event_ticket_booking.backend.service;

import com.event_ticket_booking.backend.dto.response.VoucherValidationResponse;

public interface VoucherService {
    VoucherValidationResponse validateVoucher(String code, Long userId, Long concertId);
}
