package com.sales.smartBusiness.audit;

import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.user.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes and reads the security trail.
 * <p>
 * Recording is an explicit call placed where the change happens — no AOP, no entity
 * listener. A reader of {@code UserService} can see exactly what gets logged.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuditService {

    /** Wide bounds used when the caller filters on only one side of the range. */
    private static final LocalDateTime EARLIEST = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime LATEST = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final CurrentUser currentUser;
    private final HttpServletRequest request;

    /** Records an action performed by the authenticated caller. */
    public void record(AuditAction action, AuditEntity entityType, Long entityId, String detail) {
        save(currentUser.companyId(), currentUser.id(), currentUser.email(),
                action, entityType, entityId, detail);
    }

    /**
     * Records an action attributed to a given user, for the sign-up and sign-in flows
     * where no security context exists yet.
     */
    public void recordFor(User user, AuditAction action, AuditEntity entityType,
                          Long entityId, String detail) {
        save(user.getCompany().getId(), user.getId(), user.getEmail(),
                action, entityType, entityId, detail);
    }

    /**
     * A platform admin acting on a specific company — never derived from CurrentUser,
     * since a platform admin does not belong to any company. The prefix keeps it
     * visibly distinct from a normal company user in the audit trail.
     */
    public void recordForPlatform(Long companyId, String platformAdminEmail, AuditAction action,
                                  AuditEntity entityType, Long entityId, String detail) {
        save(companyId, null, "[platform] " + platformAdminEmail, action, entityType, entityId, detail);
    }

    /**
     * A refused sign-in must survive the exception that rejects it, so it is written in
     * its own transaction.
     * <p>
     * Only recorded for an existing account: an unknown email belongs to no company, and
     * every audit line belongs to exactly one.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(User user, String detail) {
        save(user.getCompany().getId(), user.getId(), user.getEmail(),
                AuditAction.LOGIN_FAILED, AuditEntity.USER, user.getId(), detail);
    }

    /**
     * An absent date bound is widened rather than passed as null: PostgreSQL cannot
     * infer the type of a NULL timestamp parameter, exactly like a NULL inside LOWER().
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuditAction action, AuditEntity entityType, Long userId,
                                         LocalDateTime from, LocalDateTime to, Pageable pageable) {
        LocalDateTime start = from != null ? from : EARLIEST;
        LocalDateTime end = to != null ? to : LATEST;

        return auditLogRepository
                .search(currentUser.companyId(), action, entityType, userId, start, end, pageable)
                .map(auditLogMapper::toResponse);
    }

    /** The trail of a single entity — feeds the History tab of a user. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> findFor(AuditEntity entityType, Long entityId) {
        return auditLogMapper.toResponses(
                auditLogRepository.findByCompanyIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
                        currentUser.companyId(), entityType, entityId));
    }

    private void save(Long companyId, Long userId, String username, AuditAction action,
                      AuditEntity entityType, Long entityId, String detail) {
        AuditLog log = new AuditLog();
        log.setCompanyId(companyId);
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetail(detail);
        log.setIpAddress(clientIp());
        auditLogRepository.save(log);
    }

    /** Behind a reverse proxy the real address is the first entry of X-Forwarded-For. */
    private String clientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
