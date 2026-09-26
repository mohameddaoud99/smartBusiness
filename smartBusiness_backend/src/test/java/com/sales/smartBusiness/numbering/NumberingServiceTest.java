package com.sales.smartBusiness.numbering;

import com.sales.smartBusiness.audit.AuditService;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NumberingServiceTest {

    private static final Long COMPANY_ID = 7L;
    private static final int THIS_YEAR = Year.now().getValue();

    @Mock private NumberingSequenceRepository repository;
    @Mock private CompanyService companyService;
    @Mock private NumberingSequenceMapper mapper;
    @Mock private CurrentUser currentUser;
    @Mock private AuditService auditService;

    @InjectMocks private NumberingService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
    }

    private NumberingSequence sequence(String prefix, int padding, boolean includeYear,
                                       long nextValue, Integer yearOfLast) {
        NumberingSequence seq = new NumberingSequence();
        seq.setDocumentType(DocumentType.SALES_INVOICE);
        seq.setPrefix(prefix);
        seq.setPadding(padding);
        seq.setIncludeYear(includeYear);
        seq.setNextValue(nextValue);
        seq.setYearOfLast(yearOfLast);
        return seq;
    }

    @Test
    @DisplayName("format zero-pads the counter and inserts the year")
    void formatBuildsTheNumber() {
        assertThat(sequence("INV", 5, true, 42, THIS_YEAR).format(2026))
                .isEqualTo("INV-2026-00042");
        assertThat(sequence("DEV", 4, false, 7, null).format(2026))
                .isEqualTo("DEV-0007");
    }

    @Test
    @DisplayName("createDefaults seeds one sequence per document type with the default prefix")
    void createDefaultsSeedsEveryType() {
        service.createDefaults(new Company());

        verify(repository, times(DocumentType.values().length)).save(any());
        DocumentType[] types = DocumentType.values();
        verify(repository).save(argThat(seq ->
                seq.getDocumentType() == types[0] && seq.getPrefix().equals(types[0].getDefaultPrefix())));
    }

    @Test
    @DisplayName("allocate returns the current number then advances the counter")
    void allocateAdvancesTheCounter() {
        NumberingSequence seq = sequence("INV", 5, true, 42, THIS_YEAR);
        when(repository.lockByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.SALES_INVOICE))
                .thenReturn(Optional.of(seq));

        String number = service.allocate(DocumentType.SALES_INVOICE);

        assertThat(number).isEqualTo("INV-" + THIS_YEAR + "-00042");
        assertThat(seq.getNextValue()).isEqualTo(43);
        assertThat(seq.getYearOfLast()).isEqualTo(THIS_YEAR);
    }

    @Test
    @DisplayName("allocate resets the counter when the year has rolled over")
    void allocateResetsOnNewYear() {
        NumberingSequence seq = sequence("INV", 5, true, 99, THIS_YEAR - 1);
        when(repository.lockByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.SALES_INVOICE))
                .thenReturn(Optional.of(seq));

        String number = service.allocate(DocumentType.SALES_INVOICE);

        assertThat(number).isEqualTo("INV-" + THIS_YEAR + "-00001");
        assertThat(seq.getNextValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("allocate creates the sequence on the fly when none exists yet")
    void allocateCreatesMissingSequence() {
        when(repository.lockByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.SALES_INVOICE))
                .thenReturn(Optional.empty());
        when(companyService.currentReference()).thenReturn(new Company());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        String number = service.allocate(DocumentType.SALES_INVOICE);

        assertThat(number).isEqualTo("INV-" + THIS_YEAR + "-00001");
    }

    @Test
    @DisplayName("a customer code defaults to C-0001: four digits, no year")
    void customerSequenceHasNoYear() {
        when(repository.lockByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.CUSTOMER))
                .thenReturn(Optional.empty());
        when(companyService.currentReference()).thenReturn(new Company());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.allocate(DocumentType.CUSTOMER)).isEqualTo("C-0001");
    }

    @Test
    @DisplayName("findAll lists every type, showing defaults for unused ones without saving them")
    void findAllNeverWrites() {
        when(repository.findByCompanyId(COMPANY_ID))
                .thenReturn(List.of(sequence("FACT", 6, true, 12, THIS_YEAR)));
        when(mapper.toResponse(any())).thenReturn(new NumberingSequenceResponse());

        assertThat(service.findAll()).hasSize(DocumentType.values().length);

        verify(repository, never()).save(any());
        verify(mapper).toResponse(argThat(seq ->
                seq.getDocumentType() == DocumentType.SALES_INVOICE && seq.getPrefix().equals("FACT")));
        verify(mapper).toResponse(argThat(seq ->
                seq.getDocumentType() == DocumentType.QUOTE && seq.getPrefix().equals("QUO")));
    }

    @Test
    @DisplayName("update writes the new settings and an audit line")
    void updatePersistsAndAudits() {
        NumberingSequence seq = sequence("INV", 5, true, 1, null);
        when(repository.findByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.SALES_INVOICE))
                .thenReturn(Optional.of(seq));
        when(mapper.toResponse(seq)).thenReturn(new NumberingSequenceResponse());

        NumberingSequenceRequest request = new NumberingSequenceRequest();
        request.setPrefix("FACT");
        request.setPadding(6);
        request.setIncludeYear(false);
        request.setNextValue(100);
        request.setActive(true);

        service.update(DocumentType.SALES_INVOICE, request);

        assertThat(seq.getPrefix()).isEqualTo("FACT");
        assertThat(seq.getPadding()).isEqualTo(6);
        assertThat(seq.isIncludeYear()).isFalse();
        assertThat(seq.getNextValue()).isEqualTo(100);
        verify(auditService).record(any(), eq(com.sales.smartBusiness.audit.AuditEntity.NUMBERING), any(), any());
    }
}
