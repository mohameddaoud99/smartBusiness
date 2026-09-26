package com.sales.smartBusiness.audit;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogMapper auditLogMapper;
    @Mock private CurrentUser currentUser;
    @Mock private HttpServletRequest request;

    @InjectMocks private AuditService auditService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(currentUser.id()).thenReturn(42L);
        lenient().when(currentUser.email()).thenReturn("admin@abc.test");
        lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.9");
    }

    private AuditLog captured() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("an absent date bound is widened, never passed as NULL")
    void nullDatesAreWidened() {
        when(auditLogRepository.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        auditService.search(null, null, null, null, null, PageRequest.of(0, 25));

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).search(eq(COMPANY_ID), isNull(), isNull(), isNull(),
                from.capture(), to.capture(), any());

        // PostgreSQL cannot type a NULL timestamp parameter — both bounds must be real
        assertThat(from.getValue()).isNotNull().isBefore(LocalDateTime.now());
        assertThat(to.getValue()).isNotNull().isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("a supplied date bound is passed through untouched")
    void suppliedDatesArePreserved() {
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(auditLogRepository.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        auditService.search(null, null, null, from, null, PageRequest.of(0, 25));

        verify(auditLogRepository).search(eq(COMPANY_ID), isNull(), isNull(), isNull(),
                eq(from), any(), any());
    }

    @Test
    @DisplayName("a recorded line carries the caller, their company and their address")
    void recordCapturesTheCaller() {
        auditService.record(AuditAction.ROLE_CREATED, AuditEntity.ROLE, 3L, "Role created");

        AuditLog log = captured();
        assertThat(log.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(log.getUserId()).isEqualTo(42L);
        assertThat(log.getUsername()).isEqualTo("admin@abc.test");
        assertThat(log.getAction()).isEqualTo(AuditAction.ROLE_CREATED);
        assertThat(log.getEntityType()).isEqualTo(AuditEntity.ROLE);
        assertThat(log.getEntityId()).isEqualTo(3L);
        assertThat(log.getIpAddress()).isEqualTo("10.0.0.9");
    }

    @Test
    @DisplayName("behind a proxy the client address comes from X-Forwarded-For")
    void forwardedAddressWins() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("41.226.10.1, 10.0.0.9");

        auditService.record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, 1L, null);

        assertThat(captured().getIpAddress()).isEqualTo("41.226.10.1");
    }

    @Test
    @DisplayName("a refused sign-in is attributed to the account that was targeted")
    void loginFailureIsAttributedToTheAccount() {
        Company company = new Company();
        company.setId(COMPANY_ID);

        User user = new User();
        user.setId(5L);
        user.setCompany(company);
        user.setEmail("sonia@abc.tn");

        auditService.recordLoginFailure(user, "Wrong password");

        AuditLog log = captured();
        assertThat(log.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(log.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(log.getUsername()).isEqualTo("sonia@abc.tn");
        assertThat(log.getDetail()).isEqualTo("Wrong password");
    }
}
