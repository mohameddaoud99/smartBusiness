package com.sales.smartBusiness.bankaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A PUT replaces the whole account: an optional field left out is cleared, not kept.
 */
@Getter
@Setter
public class CompanyBankAccountRequest {

    @NotBlank(message = "Label is required")
    @Size(max = 120, message = "Label must not exceed 120 characters")
    private String label;

    @Size(max = 120, message = "Bank name must not exceed 120 characters")
    private String bankName;

    @NotBlank(message = "Account number (RIB or IBAN) is required")
    @Size(max = 34, message = "Account number must not exceed 34 characters")
    private String rib;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. TND")
    private String currency;

    private boolean showOnDocuments = true;
}
