package com.sales.smartBusiness.product;

import com.sales.smartBusiness.brand.Brand;
import com.sales.smartBusiness.brand.BrandService;
import com.sales.smartBusiness.category.Category;
import com.sales.smartBusiness.category.CategoryService;
import com.sales.smartBusiness.common.ImageStorage;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.DuplicateResourceException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.DocumentType;
import com.sales.smartBusiness.numbering.NumberingService;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.tax.TaxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private ProductRepository productRepository;
    @Mock private CompanyService companyService;
    @Mock private CategoryService categoryService;
    @Mock private BrandService brandService;
    @Mock private TaxService taxService;
    @Mock private NumberingService numberingService;
    @Mock private ProductMapper productMapper;
    @Mock private ImageStorage imageStorage;
    @Mock private CurrentUser currentUser;

    @InjectMocks private ProductService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(productMapper.toResponse(any())).thenAnswer(call -> new ProductResponse());
        lenient().when(taxService.resolveAssignable(any())).thenReturn(new HashSet<>());
    }

    private ProductRequest request() {
        ProductRequest request = new ProductRequest();
        request.setName("USB-C Cable");
        request.setKind(ProductKind.GOOD);
        request.setPurpose(ProductPurpose.SALE);
        request.setUnit(ProductUnit.PIECE);
        return request;
    }

    @Test
    @DisplayName("a blank reference comes from the PRODUCT numbering sequence")
    void generatesReferenceWhenBlank() {
        Product mapped = new Product();
        when(productMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.PRODUCT)).thenReturn("P-0005");
        when(productRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getReference()).isEqualTo("P-0005");
        assertThat(mapped.getCompany()).isNotNull();
    }

    @Test
    @DisplayName("a generated reference skips one somebody already typed by hand")
    void skipsTakenGeneratedReference() {
        Product mapped = new Product();
        when(productMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(numberingService.allocate(DocumentType.PRODUCT)).thenReturn("P-0001", "P-0002");
        when(productRepository.existsByCompanyIdAndReferenceIgnoreCase(COMPANY_ID, "P-0001")).thenReturn(true);
        when(productRepository.save(mapped)).thenReturn(mapped);

        service.create(request());

        assertThat(mapped.getReference()).isEqualTo("P-0002");
    }

    @Test
    @DisplayName("an explicit reference already in use is refused")
    void refusesDuplicateExplicitReference() {
        ProductRequest request = request();
        request.setReference("SKU-1");
        when(productMapper.toEntity(any())).thenReturn(new Product());
        when(productRepository.existsByCompanyIdAndReferenceIgnoreCase(COMPANY_ID, "SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("reference");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("the selected category and brand are resolved and attached")
    void resolvesCategoryAndBrand() {
        Category category = new Category();
        Brand brand = new Brand();
        Product mapped = new Product();
        ProductRequest request = request();
        request.setCategoryId(1L);
        request.setBrandId(2L);

        when(productMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(categoryService.getAssignable(1L)).thenReturn(category);
        when(brandService.getAssignable(2L)).thenReturn(brand);
        when(numberingService.allocate(DocumentType.PRODUCT)).thenReturn("P-0001");
        when(productRepository.save(mapped)).thenReturn(mapped);

        service.create(request);

        assertThat(mapped.getCategory()).isSameAs(category);
        assertThat(mapped.getBrand()).isSameAs(brand);
    }

    @Test
    @DisplayName("the requested default taxes are resolved through TaxService")
    void resolvesDefaultTaxes() {
        Product mapped = new Product();
        ProductRequest request = request();
        request.setTaxIds(Set.of(5L, 6L));
        Set<com.sales.smartBusiness.tax.Tax> taxes = Set.of(new com.sales.smartBusiness.tax.Tax());

        when(productMapper.toEntity(any())).thenReturn(mapped);
        when(companyService.currentReference()).thenReturn(new Company());
        when(taxService.resolveAssignable(Set.of(5L, 6L))).thenReturn(taxes);
        when(numberingService.allocate(DocumentType.PRODUCT)).thenReturn("P-0001");
        when(productRepository.save(mapped)).thenReturn(mapped);

        service.create(request);

        assertThat(mapped.getDefaultTaxes()).isSameAs(taxes);
    }

    @Test
    @DisplayName("a product of another company is not found")
    void productOfAnotherCompanyIsNotFound() {
        when(productRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updating without touching the reference leaves it unchanged")
    void keepsReferenceWhenNotSent() {
        Product existing = new Product();
        existing.setReference("P-0009");
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        service.update(1L, request());

        assertThat(existing.getReference()).isEqualTo("P-0009");
        verify(productRepository, never()).existsByCompanyIdAndReferenceIgnoreCaseAndIdNot(any(), any(), any());
    }

    @Test
    @DisplayName("search turns a blank term into a match-all pattern")
    void searchNormalisesBlankTerm() {
        when(productRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.search("  ", null, null, org.springframework.data.domain.Pageable.unpaged());

        verify(productRepository).search(eq(COMPANY_ID), eq("%"), isNull(), isNull(), any());
    }

    // ----- Photos -----

    private MockMultipartFile pngFile(int sizeInBytes) {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[sizeInBytes]);
    }

    private Product productWithPhotos(int count) {
        Product product = new Product();
        product.setId(1L);
        for (int i = 0; i < count; i++) {
            ProductImage image = new ProductImage();
            image.setId(100L + i);
            image.setPath("products/1/photo-" + i + ".png");
            image.setContentType("image/png");
            product.getImages().add(image);
        }
        return product;
    }

    @Test
    @DisplayName("adding a photo stores it under its own name and attaches it")
    void addImageStores() throws IOException {
        Product existing = productWithPhotos(1);
        MockMultipartFile file = pngFile(100);
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(imageStorage.store(eq("products"), eq(1L), startsWith("photo-"), eq(file)))
                .thenReturn("products/1/photo-new.png");
        when(imageStorage.read(any())).thenReturn(new byte[]{1, 2, 3});

        ProductResponse response = service.addImage(1L, file);

        assertThat(existing.getImages()).hasSize(2);
        assertThat(existing.getImages().get(1).getPath()).isEqualTo("products/1/photo-new.png");
        assertThat(response.getImages()).hasSize(2);
    }

    @Test
    @DisplayName("a fifth photo is refused before anything is written")
    void fifthPhotoIsRefused() throws IOException {
        Product existing = productWithPhotos(4);
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addImage(1L, pngFile(100)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most 4");

        verify(imageStorage, never()).store(any(), any(), any(), any());
        assertThat(existing.getImages()).hasSize(4);
    }

    @Test
    @DisplayName("a rejected image is never handed to the storage")
    void addImageStopsOnValidationFailure() throws IOException {
        Product existing = productWithPhotos(0);
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[10]);
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        doThrow(new BusinessRuleException("Only PNG, JPEG or WEBP images are allowed"))
                .when(imageStorage).assertValid(eq(file), anyLong());

        assertThatThrownBy(() -> service.addImage(1L, file))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PNG, JPEG or WEBP");

        verify(imageStorage, never()).store(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a failure to write to disk surfaces as a business rule, not a 500")
    void addImageFailureIsReported() throws IOException {
        Product existing = productWithPhotos(0);
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(imageStorage.store(eq("products"), eq(1L), any(), any())).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> service.addImage(1L, pngFile(100)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("could not be saved");
        assertThat(existing.getImages()).isEmpty();
    }

    @Test
    @DisplayName("removing a photo deletes its file and detaches it")
    void removeImageDeletesFile() throws IOException {
        Product existing = productWithPhotos(2);
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(imageStorage.read(any())).thenReturn(new byte[]{1});

        service.removeImage(1L, 100L);

        verify(imageStorage).delete("products/1/photo-0.png");
        assertThat(existing.getImages()).extracting(ProductImage::getId).containsExactly(101L);
    }

    @Test
    @DisplayName("removing a photo the product does not have is a 404")
    void removeUnknownImageIsNotFound() {
        Product existing = productWithPhotos(1);
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.removeImage(1L, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleting a product also deletes its photo files")
    void deleteRemovesPhotoFiles() throws IOException {
        Product existing = productWithPhotos(2);
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(imageStorage).delete("products/1/photo-0.png");
        verify(imageStorage).delete("products/1/photo-1.png");
        verify(productRepository).delete(existing);
    }

    @Test
    @DisplayName("the list carries only the cover photo, the detail carries all of them")
    void listHasCoverOnlyDetailHasAll() throws IOException {
        Product existing = productWithPhotos(3);
        when(imageStorage.read(any())).thenReturn(new byte[]{1});
        when(productRepository.findByIdAndCompanyId(1L, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(productRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(existing)));

        ProductResponse listed = service.search("", null, null,
                org.springframework.data.domain.Pageable.unpaged()).getContent().get(0);
        ProductResponse detail = service.findById(1L);

        assertThat(listed.getImageDataUri()).startsWith("data:image/png;base64,");
        assertThat(listed.getImages()).isEmpty();
        assertThat(detail.getImages()).hasSize(3);
        assertThat(detail.getImageDataUri()).isEqualTo(detail.getImages().get(0).getDataUri());
    }
}
