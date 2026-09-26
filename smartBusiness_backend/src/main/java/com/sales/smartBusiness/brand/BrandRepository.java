package com.sales.smartBusiness.brand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByIdAndCompanyId(Long id, Long companyId);

    List<Brand> findByCompanyIdOrderByNameAsc(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    /**
     * Blocks deletion while a product still points here. Lives here rather than in
     * ProductRepository so the brand feature never depends on the product feature
     * (products depend on brands, not the other way round).
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.brand.id = :brandId")
    long countProductsUsing(@Param("brandId") Long brandId);
}
