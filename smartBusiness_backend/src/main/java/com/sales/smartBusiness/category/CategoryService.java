package com.sales.smartBusiness.category;

import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CompanyService companyService;
    private final CategoryMapper categoryMapper;
    private final CurrentUser currentUser;

    /** Not paginated: a catalogue's family tree is small enough to load in one go. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findByCompanyIdOrderByNameAsc(currentUser.companyId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return toResponse(getCategory(id));
    }

    public CategoryResponse create(CategoryRequest request) {
        checkNameIsFree(request.getName(), null);
        Category parent = resolveParent(request.getParentId());

        Category category = categoryMapper.toEntity(request);
        category.setCompany(companyService.currentReference());
        category.setParent(parent);
        categoryRepository.save(category);

        return toResponse(category);
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getCategory(id);
        checkNameIsFree(request.getName(), id);

        Category newParent = resolveParent(request.getParentId());
        ensureNoCycle(category, newParent);

        categoryMapper.updateEntity(request, category);
        category.setParent(newParent);

        return toResponse(category);
    }

    public void delete(Long id) {
        Category category = getCategory(id);

        long children = categoryRepository.countByParentId(id);
        if (children > 0) {
            throw new BusinessRuleException(
                    "This category still has " + children + " subcategor" + (children == 1 ? "y" : "ies")
                            + ". Remove or reassign them before deleting it.");
        }
        long products = categoryRepository.countProductsUsing(id);
        if (products > 0) {
            throw new BusinessRuleException(
                    "This category is still used by " + products + " product(s). "
                            + "Reassign them before deleting it.");
        }

        categoryRepository.delete(category);
    }

    /**
     * A category another record wants to point at (a product's family). 422 rather than
     * 404: the category is a value in the caller's form, not the resource of the request.
     */
    @Transactional(readOnly = true)
    public Category getAssignable(Long id) {
        return categoryRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The selected category does not exist"));
    }

    private Category resolveParent(Long parentId) {
        return parentId == null ? null : getAssignable(parentId);
    }

    /** A category cannot become its own descendant — walk the proposed parent's chain upward. */
    private void ensureNoCycle(Category category, Category proposedParent) {
        for (Category cursor = proposedParent; cursor != null; cursor = cursor.getParent()) {
            if (cursor.getId().equals(category.getId())) {
                throw new BusinessRuleException(
                        "A category cannot be moved under one of its own subcategories");
            }
        }
    }

    private Category getCategory(Long id) {
        return categoryRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    private void checkNameIsFree(String name, Long excludedId) {
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? categoryRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name)
                : categoryRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, name, excludedId);
        if (taken) {
            throw new DuplicateResourceException("A category with this name already exists");
        }
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse response = categoryMapper.toResponse(category);
        response.setProductCount(categoryRepository.countProductsUsing(category.getId()));
        return response;
    }
}
