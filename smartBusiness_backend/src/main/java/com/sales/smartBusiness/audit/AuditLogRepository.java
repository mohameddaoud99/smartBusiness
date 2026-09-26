package com.sales.smartBusiness.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * The audit screen: one company, newest first, with optional filters.
     * <p>
     * The date bounds are never null — PostgreSQL cannot infer the type of a NULL
     * timestamp parameter in `:from IS NULL`, so the service widens an absent bound
     * instead. Enum and id parameters are safe: Hibernate binds them with a known type.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.companyId = :companyId
              AND a.occurredAt BETWEEN :from AND :to
              AND (:action     IS NULL OR a.action     = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:userId     IS NULL OR a.userId     = :userId)
            """)
    Page<AuditLog> search(@Param("companyId") Long companyId,
                          @Param("action") AuditAction action,
                          @Param("entityType") AuditEntity entityType,
                          @Param("userId") Long userId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);

    /** The History tab of one user, or of any other single entity. */
    List<AuditLog> findByCompanyIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            Long companyId, AuditEntity entityType, Long entityId);
}
