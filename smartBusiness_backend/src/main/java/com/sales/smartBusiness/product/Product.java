package com.sales.smartBusiness.product;

import com.sales.smartBusiness.brand.Brand;
import com.sales.smartBusiness.category.Category;
import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.tax.Tax;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Something the company sells or buys — a physical good or a service. Reachable only
 * from inside its own company. The reference is unique per company regardless of case
 * (index uk_products_company_reference_ci).
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Human-friendly code (SKU), unique inside the company. Generated from the PRODUCT numbering when left blank. */
    @Column(nullable = false, length = 30)
    private String reference;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 60)
    private String barcode;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(name = "sale_price", precision = 12, scale = 3)
    private BigDecimal salePrice;

    @Column(name = "purchase_price", precision = 12, scale = 3)
    private BigDecimal purchasePrice;

    /**
     * Only meaningful for a {@link ProductKind#GOOD} once the stock module exists:
     * whether a sale may be validated past the available quantity. Checked by default,
     * matching Finco's "Allow Empty Stock".
     */
    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock = true;

    /**
     * Below this available quantity the product is flagged "low stock" — one figure for the
     * whole company, not per warehouse. Null means no alert. Only meaningful for a good.
     */
    @Column(name = "min_stock", precision = 12, scale = 3)
    private BigDecimal minStock;

    /**
     * Pre-selected when the product is added to a document line — a snapshot is taken
     * at that point, this set only supplies the default (Phase 4+).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_taxes",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tax_id"))
    @BatchSize(size = 50)
    private Set<Tax> defaultTaxes = new HashSet<>();

    /** Up to {@link ProductService#MAX_IMAGES} photos, oldest first — the first one is the cover. */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<ProductImage> images = new ArrayList<>();

    /** Internal note, never shown on a document. */
    @Column(columnDefinition = "TEXT")
    private String notes;
}
