package com.sales.smartBusiness.customer;

import com.sales.smartBusiness.common.Address;
import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Someone the company sells to. Reachable only from inside its own company.
 * The reference is unique per company regardless of case (index uk_customers_company_reference_ci).
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerType type;

    /** Human-friendly code, unique inside the company. Generated from the CUSTOMER numbering when left blank. */
    @Column(nullable = false, length = 30)
    private String reference;

    /** Business name for a company, full name for an individual. */
    @Column(nullable = false, length = 150)
    private String name;

    /** Person to contact at a company customer. Not used for an individual. */
    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    /** Tax registration number ("matricule fiscal") — a company customer. Free text. */
    @Column(name = "tax_id", length = 30)
    private String taxId;

    /** National id card number ("CIN") — an individual customer. */
    @Column(name = "national_id", length = 30)
    private String nationalId;

    /** An individual customer's date of birth. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** Reference of a VAT-suspension permit, when the customer holds one. */
    @Column(name = "vat_suspension_number", length = 60)
    private String vatSuspensionNumber;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "billing_street", length = 255)),
            @AttributeOverride(name = "city", column = @Column(name = "billing_city", length = 100)),
            @AttributeOverride(name = "region", column = @Column(name = "billing_region", length = 100)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "billing_postal_code", length = 20)),
            @AttributeOverride(name = "country", column = @Column(name = "billing_country", length = 60))
    })
    private Address billingAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "shipping_street", length = 255)),
            @AttributeOverride(name = "city", column = @Column(name = "shipping_city", length = 100)),
            @AttributeOverride(name = "region", column = @Column(name = "shipping_region", length = 100)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "shipping_postal_code", length = 20)),
            @AttributeOverride(name = "country", column = @Column(name = "shipping_country", length = 60))
    })
    private Address shippingAddress;

    /** Internal note, never shown on a document. */
    @Column(columnDefinition = "TEXT")
    private String notes;
}
