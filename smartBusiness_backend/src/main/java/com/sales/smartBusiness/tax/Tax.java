package com.sales.smartBusiness.tax;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A tax the company can put on its documents — a VAT rate, a percentage surcharge
 * such as FODEC, or a flat charge such as stamp duty. Nothing here is hard-coded in
 * the calculation: rates, the amount and whether a surcharge enlarges the VAT base
 * are all read from this row, so a change in the finance law is a data edit.
 */
@Entity
@Table(name = "taxes")
@Getter
@Setter
@NoArgsConstructor
public class Tax extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaxKind kind;

    /** Percentage, for {@code VAT_RATE} and {@code PERCENTAGE_SURCHARGE}. Null otherwise. */
    @Column(precision = 6, scale = 3)
    private BigDecimal rate;

    /** Flat amount, for {@code FIXED_PER_DOCUMENT}. Null otherwise. */
    @Column(precision = 12, scale = 3)
    private BigDecimal amount;

    /** Only meaningful for a surcharge: does it get added to the base the VAT is computed on. */
    @Column(name = "included_in_vat_base", nullable = false)
    private boolean includedInVatBase = false;

    /** Pre-selected when a new document is created. */
    @Column(name = "active_by_default", nullable = false)
    private boolean activeByDefault = false;

    /** Whether the tax can be picked at all. */
    @Column(nullable = false)
    private boolean active = true;

    /** Part of the standard set seeded for every company — cannot be deleted or retyped. */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    /** Derived: a VAT rate and a surcharge sit on lines, a flat charge on the whole document. */
    public boolean appliesToLine() {
        return kind != TaxKind.FIXED_PER_DOCUMENT;
    }
}
