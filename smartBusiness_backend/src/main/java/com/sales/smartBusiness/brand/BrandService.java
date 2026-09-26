package com.sales.smartBusiness.brand;

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
public class BrandService {

    private final BrandRepository brandRepository;
    private final CompanyService companyService;
    private final BrandMapper brandMapper;
    private final CurrentUser currentUser;

    /** Not paginated: a company's brand list is small enough to load in one go. */
    @Transactional(readOnly = true)
    public List<BrandResponse> findAll() {
        return brandRepository.findByCompanyIdOrderByNameAsc(currentUser.companyId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandResponse findById(Long id) {
        return toResponse(getBrand(id));
    }

    public BrandResponse create(BrandRequest request) {
        checkNameIsFree(request.getName(), null);

        Brand brand = brandMapper.toEntity(request);
        brand.setCompany(companyService.currentReference());
        brandRepository.save(brand);

        return toResponse(brand);
    }

    public BrandResponse update(Long id, BrandRequest request) {
        Brand brand = getBrand(id);
        checkNameIsFree(request.getName(), id);

        brandMapper.updateEntity(request, brand);

        return toResponse(brand);
    }

    public void delete(Long id) {
        Brand brand = getBrand(id);

        long products = brandRepository.countProductsUsing(id);
        if (products > 0) {
            throw new BusinessRuleException(
                    "This brand is still used by " + products + " product(s). "
                            + "Reassign them before deleting it.");
        }

        brandRepository.delete(brand);
    }

    /**
     * A brand another record wants to point at (a product's manufacturer). 422 rather
     * than 404: the brand is a value in the caller's form, not the resource of the request.
     */
    @Transactional(readOnly = true)
    public Brand getAssignable(Long id) {
        return brandRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The selected brand does not exist"));
    }

    private Brand getBrand(Long id) {
        return brandRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Brand", id));
    }

    private void checkNameIsFree(String name, Long excludedId) {
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? brandRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name)
                : brandRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, name, excludedId);
        if (taken) {
            throw new DuplicateResourceException("A brand with this name already exists");
        }
    }

    private BrandResponse toResponse(Brand brand) {
        BrandResponse response = brandMapper.toResponse(brand);
        response.setProductCount(brandRepository.countProductsUsing(brand.getId()));
        return response;
    }
}
