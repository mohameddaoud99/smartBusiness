package com.sales.smartBusiness.numbering;

import com.sales.smartBusiness.audit.AuditAction;
import com.sales.smartBusiness.audit.AuditEntity;
import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NumberingService {

    private final NumberingSequenceRepository repository;
    private final CompanyService companyService;
    private final NumberingSequenceMapper mapper;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    /**
     * One line per document type. A type the company has never used is shown with its
     * defaults without being saved — it is persisted on its first update or allocation,
     * so reading the settings never writes.
     */
    @Transactional(readOnly = true)
    public List<NumberingSequenceResponse> findAll() {
        Map<DocumentType, NumberingSequence> saved = repository.findByCompanyId(currentUser.companyId())
                .stream()
                .collect(Collectors.toMap(NumberingSequence::getDocumentType, Function.identity(),
                        (first, second) -> first, () -> new EnumMap<>(DocumentType.class)));

        return Arrays.stream(DocumentType.values())
                .map(type -> saved.getOrDefault(type, newSequence(null, type)))
                .map(mapper::toResponse)
                .toList();
    }

    public NumberingSequenceResponse update(DocumentType type, NumberingSequenceRequest request) {
        NumberingSequence sequence = repository
                .findByCompanyIdAndDocumentType(currentUser.companyId(), type)
                .orElseGet(() -> createDefault(type));

        sequence.setPrefix(request.getPrefix().trim());
        sequence.setPadding(request.getPadding());
        sequence.setIncludeYear(request.isIncludeYear());
        sequence.setNextValue(request.getNextValue());
        sequence.setActive(request.isActive());

        auditService.record(AuditAction.NUMBERING_UPDATED, AuditEntity.NUMBERING, sequence.getId(),
                type.getLabel() + " numbering updated");
        return mapper.toResponse(sequence);
    }

    /**
     * Reserves the next number. Documents call it at validation time, never at draft
     * creation, so a discarded draft leaves no gap. The row lock serialises concurrent
     * calls for the same company and type.
     */
    public String allocate(DocumentType type) {
        NumberingSequence sequence = repository.lockByCompanyIdAndDocumentType(currentUser.companyId(), type)
                .orElseGet(() -> createDefault(type));
        return sequence.allocate(Year.now().getValue());
    }

    /** Every type gets its sequence for a brand new company. */
    public void createDefaults(Company company) {
        for (DocumentType type : DocumentType.values()) {
            repository.save(newSequence(company, type));
        }
    }

    private NumberingSequence createDefault(DocumentType type) {
        return repository.save(newSequence(companyService.currentReference(), type));
    }

    private NumberingSequence newSequence(Company company, DocumentType type) {
        NumberingSequence sequence = new NumberingSequence();
        sequence.setCompany(company);
        sequence.setDocumentType(type);
        sequence.setPrefix(type.getDefaultPrefix());
        sequence.setPadding(type.getDefaultPadding());
        sequence.setIncludeYear(type.isDefaultIncludeYear());
        sequence.setNextValue(1);
        sequence.setActive(true);
        return sequence;
    }
}
