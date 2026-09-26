package com.sales.smartBusiness.stock;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.product.Product;
import com.sales.smartBusiness.warehouse.Warehouse;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line of the stock register. Append-only: a movement is never edited or deleted —
 * a mistake is corrected by writing the opposite one. A product's stock is the sum of its
 * movements, never a counter that could drift.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMovementType type;

    /** Signed: positive adds to the stock, negative removes from it. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(length = 255)
    private String reason;

    /** The document that caused it. Null for a movement recorded by hand. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 30)
    private StockSource sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    /**
     * The document that caused a movement it does not own: a delivery note that released part of a
     * sales order's reservation. Null otherwise.
     */
    @Column(name = "origin_id")
    private Long originId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
