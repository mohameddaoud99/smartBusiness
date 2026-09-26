package com.sales.smartBusiness.bankaccount;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyBankAccountResponse {

    private Long id;
    private String label;
    private String bankName;
    private String rib;
    private String currency;
    private boolean showOnDocuments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
