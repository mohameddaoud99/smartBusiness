package com.sales.smartBusiness.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndCompanyId(Long id, Long companyId);

    List<Category> findByCompanyIdOrderByNameAsc(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(Long companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long id);

    /** Blocks deletion while a subcategory still points here. */
    long countByParentId(Long parentId);

    /**
     * Blocks deletion while a product still points here. Lives here rather than in
     * ProductRepository so the category feature never depends on the product feature
     * (products depend on categories, not the other way round).
     */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.id = :categoryId")
    long countProductsUsing(@Param("categoryId") Long categoryId);
}
