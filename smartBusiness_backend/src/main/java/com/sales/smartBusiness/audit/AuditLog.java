package com.sales.smartBusiness.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One line of the company security trail. Append-only, so it does not extend BaseEntity:
 * an audit row is never updated and {@code occurredAt} is the only date it needs.
 * <p>
 * Company and user are stored as plain ids rather than associations: a log line is written
 * on nearly every mutation and never navigates to the entities it names.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    /** Null only for a failed sign-in, where nobody is authenticated yet. */
    private Long userId;

    /** Snapshot of the email used, so the line stays readable if the account changes. */
    @Column(nullable = false, length = 150)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AuditEntity entityType;

    private Long entityId;

    /** Human-readable sentence, e.g. "Roles changed from Viewer to Sales Manager". */
    @Column(length = 255)
    private String detail;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        occurredAt = LocalDateTime.now();
    }
}
