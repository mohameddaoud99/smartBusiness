package com.sales.smartBusiness.company;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Business rules around the company profile: image validation, and that removing an
 * image nobody set is a harmless no-op rather than a spurious audit line.
 */
@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private CompanyRepository companyRepository;
    @Mock private CompanyMapper companyMapper;
    @Mock private CompanyImageStorage imageStorage;
    @Mock private CurrentUser currentUser;
    @Mock private AuditService auditService;

    @InjectMocks private CompanyService companyService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(COMPANY_ID);
        company.setName("ABC Distribution");

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        lenient().when(companyMapper.toResponse(any())).thenReturn(new CompanyResponse());
    }

    private MockMultipartFile pngFile(int sizeInBytes) {
        return new MockMultipartFile("file", "logo.png", "image/png", new byte[sizeInBytes]);
    }

    // ----- Text fields -----

    @Test
    @DisplayName("update maps the request onto the company and records the change")
    void updateRecordsAudit() {
        CompanyRequest request = new CompanyRequest();
        request.setName("ABC Distribution SARL");
        request.setCurrency("TND");

        companyService.update(request);

        verify(companyMapper).updateEntity(request, company);
        verify(auditService).record(eq(AuditAction.COMPANY_UPDATED), eq(AuditEntity.COMPANY),
                eq(COMPANY_ID), any());
    }

    // ----- Logo -----

    @Test
    @DisplayName("uploading a logo stores it and records the change")
    void uploadLogoStores() throws IOException {
        MockMultipartFile file = pngFile(100);
        when(imageStorage.store(COMPANY_ID, "logo", file)).thenReturn("companies/7/logo.png");
        // toResponse() reads the file straight back to build the data URI
        when(imageStorage.read("companies/7/logo.png")).thenReturn(new byte[]{1, 2, 3});

        companyService.uploadLogo(file);

        assertThat(company.getLogoPath()).isEqualTo("companies/7/logo.png");
        assertThat(company.getLogoContentType()).isEqualTo("image/png");
        verify(auditService).record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, COMPANY_ID, "Logo updated");
    }

    @Test
    @DisplayName("a non-image content type is rejected before touching the disk")
    void uploadLogoRejectsWrongType() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[10]);

        assertThatThrownBy(() -> companyService.uploadLogo(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PNG, JPEG or WEBP");

        verifyNoInteractions(imageStorage);
    }

    @Test
    @DisplayName("an oversized image is rejected before touching the disk")
    void uploadLogoRejectsOversize() {
        MockMultipartFile file = pngFile(1_500_000);

        assertThatThrownBy(() -> companyService.uploadLogo(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1 MB");

        verifyNoInteractions(imageStorage);
    }

    @Test
    @DisplayName("removing a logo that was never set is a harmless no-op")
    void removeLogoWithoutOneIsNoOp() {
        companyService.removeLogo();

        verifyNoInteractions(imageStorage);
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @DisplayName("removing an existing logo deletes the file and records the change")
    void removeLogoDeletesFile() {
        company.setLogoPath("companies/7/logo.png");
        company.setLogoContentType("image/png");

        companyService.removeLogo();

        verify(imageStorage).delete("companies/7/logo.png");
        assertThat(company.getLogoPath()).isNull();
        assertThat(company.getLogoContentType()).isNull();
        verify(auditService).record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, COMPANY_ID, "Logo removed");
    }

    // ----- Stamp — same rules, kept separate from the logo -----

    @Test
    @DisplayName("uploading a stamp stores it under its own kind, independently of the logo")
    void uploadStampStores() throws IOException {
        company.setLogoPath("companies/7/logo.png");
        MockMultipartFile file = pngFile(100);
        when(imageStorage.store(COMPANY_ID, "stamp", file)).thenReturn("companies/7/stamp.png");
        when(imageStorage.read("companies/7/stamp.png")).thenReturn(new byte[]{1, 2, 3});

        companyService.uploadStamp(file);

        assertThat(company.getStampPath()).isEqualTo("companies/7/stamp.png");
        assertThat(company.getLogoPath()).isEqualTo("companies/7/logo.png");
        verify(auditService).record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, COMPANY_ID, "Stamp updated");
    }

    @Test
    @DisplayName("a failure to write to disk surfaces as a business rule, not a 500")
    void uploadFailureIsReported() throws IOException {
        MockMultipartFile file = pngFile(100);
        when(imageStorage.store(eq(COMPANY_ID), eq("logo"), any())).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> companyService.uploadLogo(file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("could not be saved");
    }
}
