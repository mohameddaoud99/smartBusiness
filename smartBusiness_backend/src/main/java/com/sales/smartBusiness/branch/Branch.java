package com.sales.smartBusiness.branch;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A site of the company (shop, warehouse, agency). Branches organise business data;
 * they play no part in the permission model — permissions always apply company-wide.
 */
@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BranchStatus status = BranchStatus.ACTIVE;

    /**
     * Every company has at least one site, so registration creates this one.
     * It keeps the branch selector of the future sales screens from starting empty.
     */
    public static Branch main(Company company) {
        Branch branch = new Branch();
        branch.company = company;
        branch.code = "MAIN";
        branch.name = "Main branch";
        return branch;
    }
}
