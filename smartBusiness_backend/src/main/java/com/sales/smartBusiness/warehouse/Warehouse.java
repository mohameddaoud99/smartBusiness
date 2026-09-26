package com.sales.smartBusiness.warehouse;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.company.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A place stock is kept. Every company has a default warehouse from the day it registers —
 * documents that name no warehouse (an order reserving stock) use it. Reachable only from
 * inside its own company; the name is unique per company regardless of case
 * (index uk_warehouses_company_name_ci).
 */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String address;

    /** The one warehouse of the company documents fall back on — cannot be deleted or deactivated. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultWarehouse = false;

    /** An inactive warehouse keeps its history but receives no new movement. */
    @Column(nullable = false)
    private boolean active = true;
}
