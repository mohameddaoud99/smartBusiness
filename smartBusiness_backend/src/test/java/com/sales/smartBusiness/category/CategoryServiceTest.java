package com.sales.smartBusiness.category;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private CategoryRepository categoryRepository;
    @Mock private CompanyService companyService;
    @Mock private CategoryMapper categoryMapper;
    @Mock private CurrentUser currentUser;

    @InjectMocks private CategoryService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(categoryMapper.toResponse(any())).thenReturn(new CategoryResponse());
    }

    private CategoryRequest request(String name, Long parentId) {
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        request.setParentId(parentId);
        return request;
    }

    private Category category(Long id, String name, Category parent) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setParent(parent);
        return category;
    }

    @Test
    @DisplayName("a new top-level category is attached to the caller's company")
    void createTopLevelAttachesCompany() {
        Category mapped = new Category();
        when(categoryMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(categoryRepository.countProductsUsing(any())).thenReturn(0L);

        service.create(request("Electronics", null));

        assertThat(mapped.getCompany()).isNotNull();
        assertThat(mapped.getParent()).isNull();
    }

    @Test
    @DisplayName("a subcategory is attached to its resolved parent")
    void createSubcategoryResolvesParent() {
        Category parent = category(1L, "Electronics", null);
        Category mapped = new Category();
        when(categoryMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(categoryRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(parent));
        when(categoryRepository.countProductsUsing(any())).thenReturn(0L);

        service.create(request("Phones", 1L));

        assertThat(mapped.getParent()).isSameAs(parent);
    }

    @Test
    @DisplayName("a parent from another company cannot be assigned")
    void parentOutsideTheCompanyIsRejected() {
        when(categoryRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("Phones", 99L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("a name colliding with an existing category is refused")
    void duplicateNameIsRefused() {
        when(categoryRepository.existsByCompanyIdAndNameIgnoreCase(COMPANY_ID, "Electronics")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Electronics", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a category cannot become its own parent")
    void selfParentIsRejected() {
        Category existing = category(1L, "Electronics", null);
        when(categoryRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L, request("Electronics", 1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own subcategories");
    }

    @Test
    @DisplayName("a category cannot be moved under one of its own descendants")
    void deepCycleIsRejected() {
        Category grandparent = category(1L, "Electronics", null);
        Category parent = category(2L, "Phones", grandparent);
        Category child = category(3L, "Smartphones", parent);

        when(categoryRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(grandparent));
        // Attempting to move "Electronics" under its own grandchild "Smartphones"
        when(categoryRepository.findByIdAndCompanyId(3L, COMPANY_ID)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.update(1L, request("Electronics", 3L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("own subcategories");
    }

    @Test
    @DisplayName("moving a category under an unrelated one is allowed")
    void unrelatedReparentingIsAllowed() {
        Category existing = category(1L, "Phones", null);
        Category newParent = category(2L, "Electronics", null);
        when(categoryRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByIdAndCompanyId(2L, COMPANY_ID)).thenReturn(Optional.of(newParent));
        when(categoryRepository.countProductsUsing(any())).thenReturn(0L);

        service.update(1L, request("Phones", 2L));

        assertThat(existing.getParent()).isSameAs(newParent);
    }

    @Test
    @DisplayName("a category of another company is not found")
    void categoryOfAnotherCompanyIsNotFound() {
        when(categoryRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a category with subcategories cannot be deleted")
    void categoryWithChildrenCannotBeDeleted() {
        Category existing = category(1L, "Electronics", null);
        when(categoryRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.countByParentId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("subcategories");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a category still used by products cannot be deleted")
    void categoryInUseCannotBeDeleted() {
        Category existing = category(1L, "Electronics", null);
        when(categoryRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.countByParentId(1L)).thenReturn(0L);
        when(categoryRepository.countProductsUsing(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3 product(s)");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("an unused leaf category is deleted")
    void unusedCategoryIsDeleted() {
        Category existing = category(1L, "Electronics", null);
        when(categoryRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.countByParentId(1L)).thenReturn(0L);
        when(categoryRepository.countProductsUsing(1L)).thenReturn(0L);

        service.delete(1L);

        verify(categoryRepository).delete(existing);
    }
}
