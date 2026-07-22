package com.event_ticket_booking.backend.dto.response;

import com.event_ticket_booking.backend.entity.OperationLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OperationLogResponse {
    private Long id;
    private Long operatorId;
    private String action;
    private String oldValue;
    private String newValue;
    private String note;
    private LocalDateTime createdAt;

    public static OperationLogResponse fromEntity(OperationLog log) {
        return OperationLogResponse.builder()
                .id(log.getId())
                .operatorId(log.getOperator().getId())
                .action(log.getAction())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .note(log.getNote())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
