package com.sales.smartBusiness.bankaccount;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A bank account of the company itself — the one printed in the footer of sales
 * documents and used to record incoming payments. Not the same thing as a customer's
 * or supplier's own account, which belongs to that party.
 */
@Entity
@Table(name = "company_bank_accounts")
@Getter
@Setter
@NoArgsConstructor
public class CompanyBankAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    /** RIB (20 digits in Tunisia) or IBAN — free text, the format varies by country. */
    @Column(nullable = false, length = 34)
    private String rib;

    /** ISO 4217 code — the form defaults it to the company currency. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Whether this account is shown in the footer of sales documents. */
    @Column(name = "show_on_documents", nullable = false)
    private boolean showOnDocuments = true;
}
