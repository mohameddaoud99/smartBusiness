package com.sales.smartBusiness.company;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Singular and without an id: the caller's company is read from their token, so there
 * is no path through which another company could be addressed.
 */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMPANY_VIEW')")
    public CompanyResponse find() {
        return companyService.find();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public CompanyResponse update(@Valid @RequestBody CompanyRequest request) {
        return companyService.update(request);
    }

    @PostMapping("/logo")
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public CompanyResponse uploadLogo(@RequestParam("file") MultipartFile file) {
        return companyService.uploadLogo(file);
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public CompanyResponse removeLogo() {
        return companyService.removeLogo();
    }

    @PostMapping("/stamp")
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public CompanyResponse uploadStamp(@RequestParam("file") MultipartFile file) {
        return companyService.uploadStamp(file);
    }

    @DeleteMapping("/stamp")
    @PreAuthorize("hasAuthority('COMPANY_UPDATE')")
    public CompanyResponse removeStamp() {
        return companyService.removeStamp();
    }
}
