package com.sales.smartBusiness.category;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A product family, optionally nested under another one ("Family / Subfamily" in the
 * Finco reference). Reachable only from inside its own company. The name is unique per
 * company regardless of case (index uk_categories_company_name_ci), regardless of
 * nesting level — simpler than scoping uniqueness per parent, and enough for a small
 * catalogue.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

    /** Null for a top-level family. A subfamily points at its family. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
}
