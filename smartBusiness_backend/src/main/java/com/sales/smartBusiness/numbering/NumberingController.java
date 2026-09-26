package com.sales.smartBusiness.numbering;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Company-wide document numbering, guarded by the company-settings permissions.
 * There is one sequence per document type; it is never created or deleted here.
 */
@RestController
@RequestMapping("/api/settings/numbering")
@RequiredArgsConstructor
public class NumberingController {

    private final NumberingService numberingService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMPANY_VIEW')")
    public List<NumberingSequenceResponse> findAll() {
        return numberingService.findAll();
    }

    @PutMapping("/{documentType}")
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public NumberingSequenceResponse update(@PathVariable DocumentType documentType,
                                            @Valid @RequestBody NumberingSequenceRequest request) {
        return numberingService.update(documentType, request);
    }
}
