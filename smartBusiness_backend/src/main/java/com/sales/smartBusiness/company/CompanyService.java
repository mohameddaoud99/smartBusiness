package com.sales.smartBusiness.company;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_IMAGE_SIZE = 1_000_000; // 1 MB — a logo or a stamp, not a poster

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CompanyImageStorage imageStorage;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public CompanyResponse find() {
        return toResponse(getCompany());
    }

    /**
     * The caller's company as a lazy reference, to attach a new record to it without
     * loading the row. Other features go through this rather than CompanyRepository.
     */
    public Company currentReference() {
        return companyRepository.getReferenceById(currentUser.companyId());
    }

    /** Sign-up: every business module is enabled; a platform admin narrows it down later if needed. */
    public Company register(String name, String email) {
        Company company = new Company();
        company.setName(name);
        company.setEmail(email);
        company.setStatus(CompanyStatus.ACTIVE);
        company.setEnabledModules(BusinessModule.allPermissionModules());
        return companyRepository.save(company);
    }

    public CompanyResponse update(CompanyRequest request) {
        Company company = getCompany();
        companyMapper.updateEntity(request, company);

        auditService.record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, company.getId(),
                "Company settings updated");

        return toResponse(company);
    }

    public CompanyResponse uploadLogo(MultipartFile file) {
        Company company = getCompany();
        company.setLogoPath(store(company, "logo", file));
        company.setLogoContentType(file.getContentType());

        auditService.record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, company.getId(), "Logo updated");
        return toResponse(company);
    }

    public CompanyResponse removeLogo() {
        Company company = getCompany();
        if (company.getLogoPath() != null) {
            imageStorage.delete(company.getLogoPath());
            company.setLogoPath(null);
            company.setLogoContentType(null);
            auditService.record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, company.getId(), "Logo removed");
        }
        return toResponse(company);
    }

    public CompanyResponse uploadStamp(MultipartFile file) {
        Company company = getCompany();
        company.setStampPath(store(company, "stamp", file));
        company.setStampContentType(file.getContentType());

        auditService.record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, company.getId(), "Stamp updated");
        return toResponse(company);
    }

    public CompanyResponse removeStamp() {
        Company company = getCompany();
        if (company.getStampPath() != null) {
            imageStorage.delete(company.getStampPath());
            company.setStampPath(null);
            company.setStampContentType(null);
            auditService.record(AuditAction.COMPANY_UPDATED, AuditEntity.COMPANY, company.getId(), "Stamp removed");
        }
        return toResponse(company);
    }

    /** Never by id: a user only ever reaches their own company. */
    private Company getCompany() {
        return companyRepository.findById(currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Company", currentUser.companyId()));
    }

    /** {@link CompanyImageStorage#store} replaces any previous file of the same kind on its own. */
    private String store(Company company, String kind, MultipartFile file) {
        validateImage(file);
        try {
            return imageStorage.store(company.getId(), kind, file);
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be saved");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please choose an image to upload");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessRuleException("Only PNG, JPEG or WEBP images are allowed");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessRuleException("Image must not exceed 1 MB");
        }
    }

    private CompanyResponse toResponse(Company company) {
        CompanyResponse response = companyMapper.toResponse(company);
        response.setLogoDataUri(readDataUri(company.getLogoPath(), company.getLogoContentType()));
        response.setStampDataUri(readDataUri(company.getStampPath(), company.getStampContentType()));
        return response;
    }

    /**
     * A file missing from disk should not break the settings screen — the row still has
     * a path, so the next upload or removal will clean it up.
     */
    private String readDataUri(String path, String contentType) {
        if (path == null || contentType == null) {
            return null;
        }
        try {
            byte[] bytes = imageStorage.read(path);
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("Could not read stored image at {}", path, e);
            return null;
        }
    }
}
