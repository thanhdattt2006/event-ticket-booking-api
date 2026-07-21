package com.event_ticket_booking.backend.controller;

import com.event_ticket_booking.backend.dto.response.ApiResponse;
import com.event_ticket_booking.backend.dto.response.VoucherValidationResponse;
import com.event_ticket_booking.backend.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
@Tag(name = "Vouchers", description = "Voucher validation APIs")
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping("/{code}/validate")
    @Operation(summary = "Validate voucher", description = "Checks if a voucher is usable based on expiration, quota, and per-user limits")
    public ApiResponse<VoucherValidationResponse> validateVoucher(
            @PathVariable String code,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long concertId) {

        VoucherValidationResponse response = voucherService.validateVoucher(code, userId, concertId);
        return ApiResponse.ok(response);
    }
}
