package com.sales.smartBusiness.product;

import com.sales.smartBusiness.brand.Brand;
import com.sales.smartBusiness.brand.BrandService;
import com.sales.smartBusiness.category.Category;
import com.sales.smartBusiness.category.CategoryService;
import com.sales.smartBusiness.common.ImageStorage;
import com.sales.smartBusiness.common.SearchPattern;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.DocumentType;
import com.sales.smartBusiness.numbering.NumberingService;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.tax.TaxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    static final int MAX_IMAGES = 4;
    private static final long MAX_IMAGE_SIZE = 1_000_000; // 1 MB — a product photo, not a poster

    private final ProductRepository productRepository;
    private final CompanyService companyService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final TaxService taxService;
    private final NumberingService numberingService;
    private final ProductMapper productMapper;
    private final ImageStorage imageStorage;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String search, ProductKind kind, Long categoryId, Pageable pageable) {
        return productRepository.search(currentUser.companyId(), SearchPattern.like(search), kind, categoryId, pageable)
                .map(this::toListResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return toResponse(getProduct(id));
    }

    public ProductResponse create(ProductRequest request) {
        Category category = resolveCategory(request.getCategoryId());
        Brand brand = resolveBrand(request.getBrandId());
        var taxes = taxService.resolveAssignable(request.getTaxIds());

        Product product = productMapper.toEntity(request);
        product.setCompany(companyService.currentReference());
        product.setReference(isBlank(request.getReference())
                ? nextFreeReference()
                : checkedReference(request.getReference(), null));
        product.setCategory(category);
        product.setBrand(brand);
        product.setDefaultTaxes(taxes);

        return toResponse(productRepository.save(product));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        Category category = resolveCategory(request.getCategoryId());
        Brand brand = resolveBrand(request.getBrandId());
        var taxes = taxService.resolveAssignable(request.getTaxIds());

        productMapper.updateEntity(request, product);
        product.setCategory(category);
        product.setBrand(brand);
        product.setDefaultTaxes(taxes);

        if (!isBlank(request.getReference())
                && !request.getReference().trim().equalsIgnoreCase(product.getReference())) {
            product.setReference(checkedReference(request.getReference(), id));
        }

        return toResponse(product);
    }

    public void delete(Long id) {
        Product product = getProduct(id);
        long lines = productRepository.countSalesDocumentLinesUsing(id);
        if (lines > 0) {
            throw new BusinessRuleException(
                    "This product appears on " + lines + " sales document line(s) and cannot be deleted.");
        }
        long purchaseLines = productRepository.countPurchaseDocumentLinesUsing(id);
        if (purchaseLines > 0) {
            throw new BusinessRuleException(
                    "This product appears on " + purchaseLines + " purchase document line(s) and cannot be deleted.");
        }
        long movements = productRepository.countStockMovementsUsing(id);
        if (movements > 0) {
            throw new BusinessRuleException(
                    "This product has " + movements + " stock movement(s) and cannot be deleted.");
        }
        product.getImages().forEach(image -> imageStorage.delete(image.getPath()));
        productRepository.delete(product);
    }

    public ProductResponse addImage(Long id, MultipartFile file) {
        Product product = getProduct(id);
        if (product.getImages().size() >= MAX_IMAGES) {
            throw new BusinessRuleException(
                    "A product can have at most " + MAX_IMAGES + " photos. Remove one first.");
        }
        imageStorage.assertValid(file, MAX_IMAGE_SIZE);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setContentType(file.getContentType());
        try {
            // A unique kind per photo: ImageStorage replaces any file of the same kind
            image.setPath(imageStorage.store("products", product.getId(),
                    "photo-" + UUID.randomUUID(), file));
        } catch (IOException e) {
            throw new BusinessRuleException("The uploaded file could not be saved");
        }
        product.getImages().add(image);
        return toResponse(product);
    }

    public ProductResponse removeImage(Long id, Long imageId) {
        Product product = getProduct(id);
        ProductImage image = product.getImages().stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Product photo", imageId));

        imageStorage.delete(image.getPath());
        product.getImages().remove(image);
        return toResponse(product);
    }

    /**
     * The products a sales document line points at: all of them must belong to the
     * caller's company and be sellable (a purchase-only product cannot be sold).
     */
    @Transactional(readOnly = true)
    public Map<Long, Product> resolveSellable(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productRepository.findByCompanyIdAndIdIn(currentUser.companyId(), productIds);
        if (products.size() != productIds.size()) {
            throw new BusinessRuleException("One of the selected products does not exist");
        }
        for (Product product : products) {
            if (product.getPurpose() == ProductPurpose.PURCHASE) {
                throw new BusinessRuleException(
                        "\"" + product.getName() + "\" is a purchase-only product and cannot be sold");
            }
        }
        return products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /**
     * The products a purchase document line points at: all of them must belong to the caller's
     * company and be purchasable (a sale-only product cannot be bought).
     */
    @Transactional(readOnly = true)
    public Map<Long, Product> resolvePurchasable(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productRepository.findByCompanyIdAndIdIn(currentUser.companyId(), productIds);
        if (products.size() != productIds.size()) {
            throw new BusinessRuleException("One of the selected products does not exist");
        }
        for (Product product : products) {
            if (product.getPurpose() == ProductPurpose.SALE) {
                throw new BusinessRuleException(
                        "\"" + product.getName() + "\" is a sale-only product and cannot be purchased");
            }
        }
        return products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /** The goods the stock screen lists (a service has no stock), with an optional low-stock filter. */
    @Transactional(readOnly = true)
    public Page<Product> searchGoods(String search, boolean lowOnly, Pageable pageable) {
        return productRepository.searchGoods(currentUser.companyId(), SearchPattern.like(search), lowOnly, pageable);
    }

    /**
     * A product a stock movement wants to point at. 422 rather than 404 (it is a value in the
     * caller's form), and a service is refused: it has no stock to move.
     */
    @Transactional(readOnly = true)
    public Product getStockable(Long id) {
        Product product = productRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new BusinessRuleException("The selected product does not exist"));
        if (product.getKind() != ProductKind.GOOD) {
            throw new BusinessRuleException("\"" + product.getName() + "\" is a service: it has no stock");
        }
        return product;
    }

    /**
     * Locks a good for the rest of the transaction. A stock check ("is there enough?") followed by a movement is two
     * steps: without this, two requests taking the last pieces at once would both pass the check.
     */
    public void lockForStock(Long id) {
        productRepository.lockByIdAndCompanyId(id, currentUser.companyId());
    }

    private Product getProduct(Long id) {
        return productRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    private Category resolveCategory(Long categoryId) {
        return categoryId == null ? null : categoryService.getAssignable(categoryId);
    }

    private Brand resolveBrand(Long brandId) {
        return brandId == null ? null : brandService.getAssignable(brandId);
    }

    /** A code the caller typed: kept as is, provided no other product of the company uses it. */
    private String checkedReference(String requested, Long excludedId) {
        String reference = requested.trim();
        Long companyId = currentUser.companyId();
        boolean taken = excludedId == null
                ? productRepository.existsByCompanyIdAndReferenceIgnoreCase(companyId, reference)
                : productRepository.existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(companyId, reference, excludedId);
        if (taken) {
            throw new DuplicateResourceException("A product with this reference already exists");
        }
        return reference;
    }

    /**
     * The next code from the PRODUCT numbering settings. The sequence row is locked, so
     * two concurrent creations never get the same code; a code someone already typed by
     * hand is skipped.
     */
    private String nextFreeReference() {
        String candidate;
        do {
            candidate = numberingService.allocate(DocumentType.PRODUCT);
        } while (productRepository.existsByCompanyIdAndReferenceIgnoreCase(currentUser.companyId(), candidate));
        return candidate;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Every photo — for a single product. */
    private ProductResponse toResponse(Product product) {
        ProductResponse response = productMapper.toResponse(product);
        for (ProductImage image : product.getImages()) {
            String dataUri = readDataUri(image.getPath(), image.getContentType());
            if (dataUri != null) {
                response.getImages().add(new ProductImageResponse(image.getId(), dataUri));
            }
        }
        if (!response.getImages().isEmpty()) {
            response.setImageDataUri(response.getImages().get(0).getDataUri());
        }
        return response;
    }

    /** The cover photo only: a list page must not carry four photos per row. */
    private ProductResponse toListResponse(Product product) {
        ProductResponse response = productMapper.toResponse(product);
        if (!product.getImages().isEmpty()) {
            ProductImage cover = product.getImages().get(0);
            response.setImageDataUri(readDataUri(cover.getPath(), cover.getContentType()));
        }
        return response;
    }

    /**
     * A file missing from disk should not break the product screen — the row still has
     * a path, so the next upload or removal will clean it up.
     */
    private String readDataUri(String path, String contentType) {
        if (path == null || contentType == null) {
            return null;
        }
        try {
            byte[] bytes = imageStorage.read(path);
            return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("Could not read stored image at {}", path, e);
            return null;
        }
    }
}
