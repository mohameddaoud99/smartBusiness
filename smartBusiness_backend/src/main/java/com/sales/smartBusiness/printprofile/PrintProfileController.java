package com.sales.smartBusiness.printprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/print-profile")
@RequiredArgsConstructor
public class PrintProfileController {

    private final PrintProfileService printProfileService;

    /** Open to whoever can see a sales or purchase document — they need it to print one. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALE_VIEW', 'PURCHASE_VIEW', 'COMPANY_VIEW')")
    public PrintProfileResponse find() {
        return printProfileService.find();
    }
}
