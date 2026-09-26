package com.sales.smartBusiness.audit;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AuditLogResponse {

    private Long id;
    private AuditAction action;
    /** Readable label of the action, so the frontend never prints an enum name. */
    private String actionLabel;
    private AuditEntity entityType;
    private Long entityId;
    private String detail;
    private Long userId;
    private String username;
    private String ipAddress;
    private LocalDateTime occurredAt;
}
