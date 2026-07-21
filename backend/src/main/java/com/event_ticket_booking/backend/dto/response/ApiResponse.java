package com.event_ticket_booking.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard envelope for every API response.
 * success=true  → data is present, error is null
 * success=false → data is null, error contains the reason
 *
 * Generic so callers can do ApiResponse<BookingDto> etc. without casting.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String error
) {
    /** Successful response with a payload */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** Success with no payload (e.g. DELETE 204-equivalent in a 200 wrapper) */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** Error response */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
