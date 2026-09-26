package com.sales.smartBusiness.company;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.role.Permission;
import com.sales.smartBusiness.role.PermissionModule;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

/**
 * The tenant. Every business row in the application belongs to exactly one company,
 * and a user never sees anything outside their own.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 100)
    private String city;

    /** "Matricule fiscale" or equivalent business tax identifier — free text, format varies by country. */
    @Column(name = "tax_id", length = 30)
    private String taxId;

    /** ISO 4217 code, e.g. "TND", "EUR". */
    @Column(nullable = false, length = 3)
    private String currency = "TND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status = CompanyStatus.ACTIVE;

    /**
     * Logo and stamp live on disk (see {@code CompanyImageStorage}) — only their path,
     * relative to the uploads root, and content type are kept here.
     */
    @Column(length = 255)
    private String logoPath;

    @Column(length = 100)
    private String logoContentType;

    /** The company seal/stamp, used to mark official documents. */
    @Column(length = 255)
    private String stampPath;

    @Column(length = 100)
    private String stampContentType;

    /**
     * Which business modules this company may use — set by a platform admin, not by
     * the company itself. Administrative modules are not listed here: they are not
     * toggleable, so their absence from this set never means "disabled".
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_modules", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "module", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<PermissionModule> enabledModules = EnumSet.noneOf(PermissionModule.class);

    /**
     * Whether this company may use what a permission is about. A permission of an administrative module is
     * always allowed; one of a business module only while a platform admin keeps that module on. This is what
     * makes a switched-off module refused by the SERVER, on every endpoint, not just hidden in the menu.
     */
    public boolean allows(Permission permission) {
        return !BusinessModule.gates(permission.getModule()) || enabledModules.contains(permission.getModule());
    }
}
