package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.customer.Customer;
import com.sales.smartBusiness.customer.CustomerService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.DocumentType;
import com.sales.smartBusiness.numbering.NumberingService;
import com.sales.smartBusiness.product.Product;
import com.sales.smartBusiness.product.ProductPurpose;
import com.sales.smartBusiness.product.ProductService;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.stock.StockRequirement;
import com.sales.smartBusiness.stock.StockService;
import com.sales.smartBusiness.stock.StockSource;
import com.sales.smartBusiness.product.ProductKind;
import com.sales.smartBusiness.tax.Tax;
import com.sales.smartBusiness.tax.TaxKind;
import com.sales.smartBusiness.tax.TaxService;
import com.sales.smartBusiness.warehouse.Warehouse;
import com.sales.smartBusiness.warehouse.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesDocumentServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private SalesDocumentRepository documentRepository;
    @Mock private CompanyService companyService;
    @Mock private CustomerService customerService;
    @Mock private ProductService productService;
    @Mock private TaxService taxService;
    @Mock private NumberingService numberingService;
    @Mock private StockService stockService;
    @Mock private WarehouseService warehouseService;
    @Mock private CurrentUser currentUser;
    @Spy private SalesDocumentMapper documentMapper = Mappers.getMapper(SalesDocumentMapper.class);

    @InjectMocks private SalesDocumentService service;

    private Company company;
    private Customer customer;

    @BeforeEach
    void setUp() {
        company = new Company();
        customer = new Customer();
        customer.setId(3L);
        customer.setName("Client Alpha");

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(companyService.currentReference()).thenReturn(company);
        lenient().when(customerService.getAssignable(3L)).thenReturn(customer);
        lenient().when(warehouseService.getAssignable(1L)).thenReturn(warehouse());
        lenient().when(warehouseService.getDefault()).thenReturn(warehouse());
        lenient().when(productService.resolveSellable(any())).thenReturn(Map.of());
        lenient().when(taxService.resolveAssignable(any())).thenReturn(Set.of());
        lenient().when(documentRepository.save(any(SalesDocument.class))).thenAnswer(call -> {
            SalesDocument saved = call.getArgument(0);
            saved.setId(11L);
            return saved;
        });
    }

    // ----- Fixtures -----

    private static Tax tax(long id, String name, TaxKind kind, String rate, String amount, boolean inVatBase) {
        Tax tax = new Tax();
        tax.setId(id);
        tax.setName(name);
        tax.setKind(kind);
        tax.setRate(rate == null ? null : new BigDecimal(rate));
        tax.setAmount(amount == null ? null : new BigDecimal(amount));
        tax.setIncludedInVatBase(inVatBase);
        tax.setActive(true);
        return tax;
    }

    private static Tax vat19() {
        return tax(10L, "TVA 19%", TaxKind.VAT_RATE, "19", null, false);
    }

    private static Tax fodec() {
        return tax(11L, "FODEC", TaxKind.PERCENTAGE_SURCHARGE, "1", null, true);
    }

    private static Tax stamp() {
        return tax(12L, "Timbre fiscal", TaxKind.FIXED_PER_DOCUMENT, null, "1.000", false);
    }

    private static SalesDocumentLineRequest freeLine(String designation, String quantity, String price, Long vatTaxId) {
        SalesDocumentLineRequest line = new SalesDocumentLineRequest();
        line.setDesignation(designation);
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(price));
        line.setVatTaxId(vatTaxId);
        return line;
    }

    private static SalesDocumentRequest request(SalesDocumentType type, SalesDocumentLineRequest... lines) {
        SalesDocumentRequest request = new SalesDocumentRequest();
        request.setType(type);
        request.setCustomerId(3L);
        request.setIssueDate(LocalDate.of(2026, 9, 20));
        request.setLines(List.of(lines));
        return request;
    }

    private SalesDocument existing(SalesDocumentType type, SalesDocumentStatus status) {
        SalesDocument document = new SalesDocument();
        document.setId(5L);
        document.setCompany(company);
        document.setCustomer(customer);
        document.setType(type);
        document.setStatus(status);
        document.setIssueDate(LocalDate.of(2026, 9, 1));

        SalesDocumentLine line = new SalesDocumentLine();
        line.setDesignation("Item");
        line.setQuantity(new BigDecimal("1"));
        line.setUnitPrice(new BigDecimal("30.000"));
        line.setDiscountRate(BigDecimal.ZERO);
        line.setVatRate(new BigDecimal("19"));
        document.addLine(line);
        document.recalculate();

        when(documentRepository.findByIdAndCompanyId(5L, COMPANY_ID)).thenReturn(Optional.of(document));
        return document;
    }

    // ----- Create -----

    @Test
    @DisplayName("create builds a draft with computed totals and no number yet")
    void createsDraft() {
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(vat19(), fodec(), stamp()));
        SalesDocumentRequest request = request(SalesDocumentType.QUOTE, freeLine("Pantalon", "1", "30.000", 10L));
        request.setTaxIds(Set.of(11L, 12L));

        SalesDocumentResponse response = service.create(request);

        assertThat(response.getStatus()).isEqualTo(SalesDocumentStatus.DRAFT);
        assertThat(response.getReference()).isNull();
        assertThat(response.getCustomerName()).isEqualTo("Client Alpha");
        assertThat(response.getSubtotal()).isEqualByComparingTo("30.000");
        assertThat(response.getTotal()).isEqualByComparingTo("37.057");
        assertThat(response.getTaxes()).extracting(SalesDocumentTaxResponse::getName)
                .containsExactly("FODEC", "VAT 19%", "Timbre fiscal");
        verifyNoInteractions(numberingService);
    }

    @Test
    @DisplayName("create stamps the company and the customer resolved through their services")
    void createStampsCompanyAndCustomer() {
        service.create(request(SalesDocumentType.QUOTE, freeLine("Item", "1", "10.000", null)));

        var saved = org.mockito.ArgumentCaptor.forClass(SalesDocument.class);
        verify(documentRepository).save(saved.capture());
        assertThat(saved.getValue().getCompany()).isSameAs(company);
        assertThat(saved.getValue().getCustomer()).isSameAs(customer);
        assertThat(saved.getValue().getType()).isEqualTo(SalesDocumentType.QUOTE);
    }

    @Test
    @DisplayName("a product line takes the product's name, code and sale price when left blank")
    void productLineTakesProductDefaults() {
        Product product = new Product();
        product.setId(20L);
        product.setName("USB-C Cable");
        product.setReference("P-0020");
        product.setSalePrice(new BigDecimal("12.500"));
        product.setPurpose(ProductPurpose.SALE);
        when(productService.resolveSellable(Set.of(20L))).thenReturn(Map.of(20L, product));

        SalesDocumentLineRequest line = new SalesDocumentLineRequest();
        line.setProductId(20L);
        line.setQuantity(new BigDecimal("2"));

        SalesDocumentResponse response = service.create(request(SalesDocumentType.QUOTE, line));

        SalesDocumentLineResponse created = response.getLines().get(0);
        assertThat(created.getProductId()).isEqualTo(20L);
        assertThat(created.getDesignation()).isEqualTo("USB-C Cable");
        assertThat(created.getReference()).isEqualTo("P-0020");
        assertThat(created.getUnitPrice()).isEqualByComparingTo("12.500");
        assertThat(created.getLineTotal()).isEqualByComparingTo("25.000");
    }

    @Test
    @DisplayName("a price typed on the line wins over the product price")
    void typedPriceWins() {
        Product product = new Product();
        product.setId(20L);
        product.setName("USB-C Cable");
        product.setSalePrice(new BigDecimal("12.500"));
        when(productService.resolveSellable(Set.of(20L))).thenReturn(Map.of(20L, product));

        SalesDocumentLineRequest line = freeLine(null, "1", "9.000", null);
        line.setProductId(20L);

        SalesDocumentResponse response = service.create(request(SalesDocumentType.QUOTE, line));

        assertThat(response.getLines().get(0).getUnitPrice()).isEqualByComparingTo("9.000");
    }

    @Test
    @DisplayName("a free line without a designation is refused")
    void freeLineNeedsDesignation() {
        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.QUOTE, freeLine("  ", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("designation");

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("the customer is resolved through the customer service, so a foreign one is refused")
    void foreignCustomerRefused() {
        when(customerService.getAssignable(3L))
                .thenThrow(new BusinessRuleException("The selected customer does not exist"));

        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.QUOTE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class);

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a VAT picked on a line must be a VAT rate")
    void lineVatMustBeVat() {
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(fodec()));

        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.QUOTE, freeLine("Item", "1", "10.000", 11L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a VAT rate");
    }

    @Test
    @DisplayName("a VAT rate cannot be put on the whole document")
    void documentTaxCannotBeVat() {
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(vat19()));
        SalesDocumentRequest request = request(SalesDocumentType.QUOTE, freeLine("Item", "1", "10.000", null));
        request.setTaxIds(Set.of(10L));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("VAT rate");
    }

    @Test
    @DisplayName("an inactive tax cannot be used")
    void inactiveTaxRefused() {
        Tax inactive = vat19();
        inactive.setActive(false);
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(inactive));

        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.QUOTE, freeLine("Item", "1", "10.000", 10L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("a due date before the issue date is refused")
    void dueDateBeforeIssueDate() {
        SalesDocumentRequest request = request(SalesDocumentType.QUOTE, freeLine("Item", "1", "10.000", null));
        request.setDueDate(LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("due date");
    }

    // ----- Update / delete -----

    @Test
    @DisplayName("update replaces the lines and recomputes the totals")
    void updateReplacesLines() {
        SalesDocument draft = existing(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT);

        SalesDocumentResponse response = service.update(5L,
                request(SalesDocumentType.QUOTE,
                        freeLine("A", "2", "10.000", null),
                        freeLine("B", "1", "5.000", null)));

        assertThat(draft.getLines()).hasSize(2);
        assertThat(response.getSubtotal()).isEqualByComparingTo("25.000");
    }

    @Test
    @DisplayName("an issued document can no longer be edited")
    void issuedDocumentIsFrozen() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> service.update(5L,
                request(SalesDocumentType.QUOTE, freeLine("A", "1", "1.000", null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only a draft");
    }

    @Test
    @DisplayName("a document's type cannot be changed")
    void typeCannotChange() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT);

        assertThatThrownBy(() -> service.update(5L,
                request(SalesDocumentType.SALES_ORDER, freeLine("A", "1", "1.000", null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("type");
    }

    @Test
    @DisplayName("an unknown or foreign document is a 404")
    void unknownDocument() {
        when(documentRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.issue(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a draft can be deleted")
    void deletesDraft() {
        SalesDocument draft = existing(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT);

        service.delete(5L);

        verify(documentRepository).delete(draft);
    }

    @Test
    @DisplayName("an issued document is cancelled, never deleted")
    void issuedDocumentCannotBeDeleted() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(BusinessRuleException.class);

        verify(documentRepository, never()).delete(any());
    }

    // ----- Issue -----

    @Test
    @DisplayName("issuing allocates the number of the document's own type")
    void issueAllocatesNumber() {
        SalesDocument draft = existing(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT);
        when(numberingService.allocate(DocumentType.QUOTE)).thenReturn("QUO-2026-00001");

        SalesDocumentResponse response = service.issue(5L);

        assertThat(draft.getStatus()).isEqualTo(SalesDocumentStatus.ISSUED);
        assertThat(response.getReference()).isEqualTo("QUO-2026-00001");
    }

    @Test
    @DisplayName("issuing skips a number a document already carries")
    void issueSkipsTakenNumber() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.DRAFT);
        when(numberingService.allocate(DocumentType.SALES_ORDER)).thenReturn("SO-2026-00001", "SO-2026-00002");
        when(documentRepository.existsByCompanyIdAndTypeAndReferenceIgnoreCase(
                COMPANY_ID, SalesDocumentType.SALES_ORDER, "SO-2026-00001")).thenReturn(true);

        SalesDocumentResponse response = service.issue(5L);

        assertThat(response.getReference()).isEqualTo("SO-2026-00002");
    }

    @Test
    @DisplayName("a document already issued cannot be issued again")
    void issueTwiceRefused() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> service.issue(5L)).isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(numberingService);
    }

    // ----- Status -----

    @Test
    @DisplayName("a quote is accepted once issued")
    void acceptQuote() {
        SalesDocument quote = existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);

        service.changeStatus(5L, SalesDocumentStatus.ACCEPTED);

        assertThat(quote.getStatus()).isEqualTo(SalesDocumentStatus.ACCEPTED);
    }

    @Test
    @DisplayName("a draft has to be issued before its status can change")
    void draftCannotChangeStatus() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT);

        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.ACCEPTED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Issue the draft");
    }

    @Test
    @DisplayName("cancelling goes through its own operation, not the status one")
    void statusRefusesCancel() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.CANCELLED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cancel action");
    }

    @Test
    @DisplayName("an impossible transition is refused with a readable message")
    void impossibleTransition() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.CONFIRMED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quote that is issued cannot become confirmed");
    }

    @Test
    @DisplayName("a sales order is confirmed")
    void confirmOrder() {
        SalesDocument order = existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);

        service.changeStatus(5L, SalesDocumentStatus.CONFIRMED);

        assertThat(order.getStatus()).isEqualTo(SalesDocumentStatus.CONFIRMED);
    }

    // ----- Cancel -----

    @Test
    @DisplayName("a sales order can be cancelled, a confirmed one too")
    void cancelOrder() {
        SalesDocument order = existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);

        service.cancel(5L);

        assertThat(order.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
    }

    @Test
    @DisplayName("a quote is rejected, not cancelled")
    void quoteCannotBeCancelled() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    @DisplayName("a draft is deleted, not cancelled")
    void draftCannotBeCancelled() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.DRAFT);

        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a cancelled order cannot be cancelled again")
    void cancelTwiceRefused() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(BusinessRuleException.class);
    }

    // ----- Stock -----

    private void addLine(SalesDocument order, Product product, String quantity) {
        SalesDocumentLine line = new SalesDocumentLine();
        line.setProduct(product);
        line.setDesignation(product == null ? "Free" : product.getName());
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(BigDecimal.TEN);
        line.setDiscountRate(BigDecimal.ZERO);
        line.setVatRate(BigDecimal.ZERO);
        order.addLine(line);
    }

    private static Product good(long id, ProductKind kind) {
        Product product = new Product();
        product.setId(id);
        product.setName("Item " + id);
        product.setKind(kind);
        return product;
    }

    @Test
    @DisplayName("confirming an order reserves its goods, and only its goods")
    void confirmReservesGoods() {
        SalesDocument order = existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);
        addLine(order, good(20L, ProductKind.GOOD), "3");
        addLine(order, good(21L, ProductKind.SERVICE), "1");
        addLine(order, null, "2");

        service.changeStatus(5L, SalesDocumentStatus.CONFIRMED);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<StockRequirement>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stockService).reserve(eq(StockSource.SALES_DOCUMENT), eq(5L), captor.capture());
        assertThat(captor.getValue()).extracting(r -> r.product().getId()).containsExactly(20L);
        assertThat(captor.getValue().get(0).quantity()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("a refused reservation leaves the order unconfirmed")
    void refusedReservationBlocksConfirmation() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);
        doThrow(new BusinessRuleException("Not enough stock")).when(stockService).reserve(any(), any(), any());

        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.CONFIRMED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Not enough stock");
    }

    @Test
    @DisplayName("accepting a quote touches no stock")
    void acceptingQuoteTouchesNoStock() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);

        service.changeStatus(5L, SalesDocumentStatus.ACCEPTED);

        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("cancelling an order gives back what it held")
    void cancelReleasesStock() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);

        service.cancel(5L);

        verify(stockService).release(StockSource.SALES_DOCUMENT, 5L);
    }

    // ----- Convert -----

    @Test
    @DisplayName("an issued quote becomes a draft sales order linked to it, with the same total")
    void convertsQuote() {
        SalesDocument quote = existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);
        quote.setReference("QUO-2026-00001");

        SalesDocumentResponse response = service.convertToOrder(5L);

        assertThat(response.getType()).isEqualTo(SalesDocumentType.SALES_ORDER);
        assertThat(response.getStatus()).isEqualTo(SalesDocumentStatus.DRAFT);
        assertThat(response.getSourceId()).isEqualTo(5L);
        assertThat(response.getSourceReference()).isEqualTo("QUO-2026-00001");
        assertThat(response.getCustomerId()).isEqualTo(3L);
        assertThat(response.getTotal()).isEqualByComparingTo(quote.getTotal());
        assertThat(response.getLines()).hasSize(1);
    }

    @Test
    @DisplayName("an accepted quote can be converted too")
    void convertsAcceptedQuote() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ACCEPTED);

        assertThat(service.convertToOrder(5L).getType()).isEqualTo(SalesDocumentType.SALES_ORDER);
    }

    @Test
    @DisplayName("a draft or rejected quote cannot be converted")
    void draftOrRejectedQuoteNotConvertible() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToOrder(5L)).isInstanceOf(BusinessRuleException.class);

        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.REJECTED);
        assertThatThrownBy(() -> service.convertToOrder(5L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("only a quote can be converted")
    void orderNotConvertible() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);

        assertThatThrownBy(() -> service.convertToOrder(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only a quote");
    }

    @Test
    @DisplayName("a quote that already has a live order cannot be converted again")
    void convertOnlyOnce() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);
        when(documentRepository.existsBySourceIdAndCompanyIdAndStatusNot(
                5L, COMPANY_ID, SalesDocumentStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> service.convertToOrder(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a sales order");

        verify(documentRepository, never()).save(any());
    }

    // ----- Preview / search -----

    @Test
    @DisplayName("preview returns the totals without saving anything")
    void previewSavesNothing() {
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(vat19()));

        SalesDocumentResponse response = service.preview(
                request(SalesDocumentType.QUOTE, freeLine("Item", "1", "100.000", 10L)));

        assertThat(response.getTotal()).isEqualByComparingTo("119.000");
        assertThat(response.getId()).isNull();
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(numberingService);
    }

    @Test
    @DisplayName("search is scoped to the caller's company and normalises the pattern")
    void searchIsScoped() {
        Pageable pageable = PageRequest.of(0, 10);
        when(documentRepository.search(eq(COMPANY_ID), eq(SalesDocumentType.QUOTE), eq("%alpha%"),
                eq(SalesDocumentStatus.ISSUED), eq(3L), eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.search(SalesDocumentType.QUOTE, " Alpha ", SalesDocumentStatus.ISSUED, 3L, pageable);

        verify(documentRepository).search(COMPANY_ID, SalesDocumentType.QUOTE, "%alpha%",
                SalesDocumentStatus.ISSUED, 3L, pageable);
        verify(documentRepository, never()).findAll();
        verify(documentRepository, never()).findById(anyLong());
    }

    // ----- Delivery notes -----

    private static Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Main");
        return warehouse;
    }

    private SalesDocument note(SalesDocumentStatus status, SalesDocument source) {
        SalesDocument note = existing(SalesDocumentType.DELIVERY_NOTE, status);
        note.setWarehouse(warehouse());
        note.setSource(source);
        addLine(note, good(20L, ProductKind.GOOD), "4");
        return note;
    }

    private SalesDocument confirmedOrder() {
        SalesDocument order = new SalesDocument();
        order.setId(8L);
        order.setType(SalesDocumentType.SALES_ORDER);
        order.setStatus(SalesDocumentStatus.CONFIRMED);
        return order;
    }

    @Test
    @DisplayName("a delivery note needs a warehouse; the other documents get none even if one is sent")
    void deliveryNoteWarehouseRules() {
        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.DELIVERY_NOTE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("needs a warehouse");

        SalesDocumentRequest delivery = request(SalesDocumentType.DELIVERY_NOTE, freeLine("Item", "1", "10.000", null));
        delivery.setWarehouseId(1L);
        assertThat(service.create(delivery).getWarehouseName()).isEqualTo("Main");

        SalesDocumentRequest quote = request(SalesDocumentType.QUOTE, freeLine("Item", "1", "10.000", null));
        quote.setWarehouseId(1L);
        assertThat(service.create(quote).getWarehouseId()).isNull();
    }

    @Test
    @DisplayName("delivering takes the goods, and only the goods, out of the warehouse of the note")
    void deliverTakesGoodsOut() {
        SalesDocument note = note(SalesDocumentStatus.ISSUED, null);
        addLine(note, good(21L, ProductKind.SERVICE), "1");

        service.changeStatus(5L, SalesDocumentStatus.DELIVERED);

        assertThat(note.getStatus()).isEqualTo(SalesDocumentStatus.DELIVERED);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<StockRequirement>> goods = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stockService).deliver(eq(StockSource.SALES_DOCUMENT), eq(5L), eq(note.getWarehouse()), goods.capture(), isNull());
        assertThat(goods.getValue()).extracting(g -> g.product().getId()).containsExactly(20L);
        assertThat(goods.getValue().get(0).quantity()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("a delivery made from an order consumes that order's reservation")
    void deliverConsumesTheOrdersReservation() {
        note(SalesDocumentStatus.ISSUED, confirmedOrder());

        service.changeStatus(5L, SalesDocumentStatus.DELIVERED);

        verify(stockService).deliver(eq(StockSource.SALES_DOCUMENT), eq(5L), any(), any(), eq(8L));
    }

    @Test
    @DisplayName("a delivery the stock refuses leaves the note created, not delivered")
    void deliveryRefusedByStock() {
        note(SalesDocumentStatus.ISSUED, null);
        doThrow(new BusinessRuleException("Not enough stock")).when(stockService).deliver(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.DELIVERED))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Not enough stock");
    }

    @Test
    @DisplayName("only a created delivery note can be delivered — not a draft, not an order")
    void onlyCreatedNotesAreDelivered() {
        note(SalesDocumentStatus.DRAFT, null);
        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.DELIVERED))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Issue the draft");

        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.DELIVERED))
                .isInstanceOf(BusinessRuleException.class);

        verify(stockService, never()).deliver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelling a delivered note brings the goods back and restores the reservation of a standing order")
    void cancelDeliveredNote() {
        SalesDocument note = note(SalesDocumentStatus.DELIVERED, confirmedOrder());

        service.cancel(5L);

        assertThat(note.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
        verify(stockService).undoDelivery(StockSource.SALES_DOCUMENT, 5L, true);
        verify(stockService, never()).release(any(), any());
    }

    @Test
    @DisplayName("the reservation is not restored when the order was cancelled meanwhile, or when there was none")
    void cancelDeliveredNoteWithoutStandingOrder() {
        SalesDocument cancelledOrder = confirmedOrder();
        cancelledOrder.setStatus(SalesDocumentStatus.CANCELLED);
        note(SalesDocumentStatus.DELIVERED, cancelledOrder);
        service.cancel(5L);
        verify(stockService).undoDelivery(StockSource.SALES_DOCUMENT, 5L, false);

        note(SalesDocumentStatus.DELIVERED, null);
        service.cancel(5L);
        verify(stockService, times(2)).undoDelivery(StockSource.SALES_DOCUMENT, 5L, false);
    }

    @Test
    @DisplayName("cancelling a note that never left touches no stock")
    void cancelCreatedNote() {
        SalesDocument note = note(SalesDocumentStatus.ISSUED, confirmedOrder());

        service.cancel(5L);

        assertThat(note.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an order with a delivery note cannot be cancelled until the notes are")
    void orderWithDeliveryNoteCannotBeCancelled() {
        SalesDocument order = existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        SalesDocument live = new SalesDocument();
        live.setType(SalesDocumentType.DELIVERY_NOTE);
        live.setStatus(SalesDocumentStatus.DELIVERED);
        SalesDocument dead = new SalesDocument();
        dead.setType(SalesDocumentType.DELIVERY_NOTE);
        dead.setStatus(SalesDocumentStatus.CANCELLED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(dead, live));

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("delivery notes");
        verify(stockService, never()).release(any(), any());

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(dead));
        service.cancel(5L);
        assertThat(order.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
    }

    @Test
    @DisplayName("a confirmed order becomes a draft delivery note from the default warehouse, with the same lines")
    void convertsConfirmedOrder() {
        SalesDocument order = existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        order.setReference("SO-2026-00001");

        SalesDocumentResponse response = service.convertToDeliveryNote(5L);

        assertThat(response.getType()).isEqualTo(SalesDocumentType.DELIVERY_NOTE);
        assertThat(response.getStatus()).isEqualTo(SalesDocumentStatus.DRAFT);
        assertThat(response.getSourceReference()).isEqualTo("SO-2026-00001");
        assertThat(response.getWarehouseName()).isEqualTo("Main");
        assertThat(response.getTotal()).isEqualByComparingTo(order.getTotal());
        assertThat(response.getLines()).hasSize(1);
        verifyNoInteractions(stockService); // a draft moves nothing
    }

    @Test
    @DisplayName("an order can be delivered in several parts: nothing stops a second delivery note")
    void severalDeliveryNotes() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);

        service.convertToDeliveryNote(5L);
        service.convertToDeliveryNote(5L);

        verify(documentRepository, times(2)).save(any(SalesDocument.class));
    }

    @Test
    @DisplayName("only a confirmed sales order can be turned into a delivery note")
    void deliveryNoteConversionRules() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);
        assertThatThrownBy(() -> service.convertToDeliveryNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Confirm the sales order");

        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ACCEPTED);
        assertThatThrownBy(() -> service.convertToDeliveryNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a sales order");
    }

    // ----- Invoices -----

    private SalesDocument invoice(SalesDocumentStatus status, SalesDocument source) {
        SalesDocument invoice = existing(SalesDocumentType.INVOICE, status);
        invoice.setWarehouse(warehouse());
        invoice.setSource(source);
        addLine(invoice, good(20L, ProductKind.GOOD), "4");
        addLine(invoice, good(21L, ProductKind.SERVICE), "1");
        return invoice;
    }

    private SalesDocument deliveredNote() {
        SalesDocument note = new SalesDocument();
        note.setId(9L);
        note.setType(SalesDocumentType.DELIVERY_NOTE);
        note.setStatus(SalesDocumentStatus.DELIVERED);
        return note;
    }

    private static SalesDocument child(SalesDocumentType type, SalesDocumentStatus status) {
        SalesDocument child = new SalesDocument();
        child.setType(type);
        child.setStatus(status);
        return child;
    }

    @Test
    @DisplayName("an invoice needs a warehouse, like a delivery note")
    void invoiceNeedsWarehouse() {
        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.INVOICE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessage("An invoice needs a warehouse");

        SalesDocumentRequest request = request(SalesDocumentType.INVOICE, freeLine("Item", "1", "10.000", null));
        request.setWarehouseId(1L);
        assertThat(service.create(request).getWarehouseName()).isEqualTo("Main");
    }

    @Test
    @DisplayName("issuing an invoice allocates its number, then takes the goods - and only the goods - out of the stock")
    void issueInvoiceTakesGoodsOut() {
        SalesDocument invoice = invoice(SalesDocumentStatus.DRAFT, null);
        when(numberingService.allocate(DocumentType.SALES_INVOICE)).thenReturn("INV-2026-00001");

        SalesDocumentResponse response = service.issue(5L);

        assertThat(response.getReference()).isEqualTo("INV-2026-00001");
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.ISSUED);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<StockRequirement>> goods = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stockService).deliver(eq(StockSource.SALES_DOCUMENT), eq(5L), eq(invoice.getWarehouse()), goods.capture(), isNull());
        assertThat(goods.getValue()).extracting(g -> g.product().getId()).containsExactly(20L);
    }

    @Test
    @DisplayName("an invoice made from an order consumes that order's reservation")
    void issueInvoiceFromOrder() {
        invoice(SalesDocumentStatus.DRAFT, confirmedOrder());
        when(numberingService.allocate(DocumentType.SALES_INVOICE)).thenReturn("INV-2026-00001");

        service.issue(5L);

        verify(stockService).deliver(eq(StockSource.SALES_DOCUMENT), eq(5L), any(), any(), eq(8L));
    }

    @Test
    @DisplayName("an invoice made from a delivery note moves no stock: the note already took the goods out")
    void issueInvoiceFromDeliveryNote() {
        invoice(SalesDocumentStatus.DRAFT, deliveredNote());
        when(numberingService.allocate(DocumentType.SALES_INVOICE)).thenReturn("INV-2026-00001");

        service.issue(5L);

        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an invoice the stock refuses stays a draft")
    void issueInvoiceRefusedByStock() {
        invoice(SalesDocumentStatus.DRAFT, null);
        when(numberingService.allocate(DocumentType.SALES_INVOICE)).thenReturn("INV-2026-00001");
        doThrow(new BusinessRuleException("Not enough stock")).when(stockService).deliver(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.issue(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Not enough stock");
    }

    @Test
    @DisplayName("an invoice status is never changed by hand: its payments move it")
    void invoiceStatusIsNotChangedByHand() {
        invoice(SalesDocumentStatus.ISSUED, null);

        assertThatThrownBy(() -> service.changeStatus(5L, SalesDocumentStatus.PAID))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("paid by its payments");
    }

    @Test
    @DisplayName("cancelling an unpaid invoice puts its goods back, and the reservation of a standing order")
    void cancelInvoice() {
        SalesDocument invoice = invoice(SalesDocumentStatus.ISSUED, confirmedOrder());

        service.cancel(5L);

        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
        verify(stockService).undoDelivery(StockSource.SALES_DOCUMENT, 5L, true);
    }

    @Test
    @DisplayName("cancelling an invoice made from a delivery note moves no stock")
    void cancelInvoiceFromDeliveryNote() {
        SalesDocument invoice = invoice(SalesDocumentStatus.ISSUED, deliveredNote());

        service.cancel(5L);

        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an invoice with payments cannot be cancelled until they are")
    void invoiceWithPaymentsCannotBeCancelled() {
        SalesDocument invoice = invoice(SalesDocumentStatus.PARTIALLY_PAID, null);
        invoice.setPaidAmount(new BigDecimal("5.000"));

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has payments");
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an order or a delivery note with a live invoice cannot be cancelled, and one with a dead invoice can")
    void invoicedDocumentCannotBeCancelled() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED)));
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has an invoice");

        existing(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED);
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has an invoice");

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.INVOICE, SalesDocumentStatus.CANCELLED)));
        service.cancel(5L);
    }

    @Test
    @DisplayName("a delivered note becomes a draft invoice from the warehouse it left, with the same lines")
    void convertsDeliveredNote() {
        SalesDocument note = existing(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED);
        note.setReference("BL-2026-00001");
        Warehouse sfax = new Warehouse();
        sfax.setId(2L);
        sfax.setName("Sfax");
        note.setWarehouse(sfax);

        SalesDocumentResponse response = service.convertToInvoice(5L);

        assertThat(response.getType()).isEqualTo(SalesDocumentType.INVOICE);
        assertThat(response.getStatus()).isEqualTo(SalesDocumentStatus.DRAFT);
        assertThat(response.getSourceReference()).isEqualTo("BL-2026-00001");
        assertThat(response.getWarehouseName()).isEqualTo("Sfax");
        assertThat(response.getTotal()).isEqualByComparingTo(note.getTotal());
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("a confirmed order becomes a draft invoice on the default warehouse")
    void convertsConfirmedOrderToInvoice() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);

        SalesDocumentResponse response = service.convertToInvoice(5L);

        assertThat(response.getType()).isEqualTo(SalesDocumentType.INVOICE);
        assertThat(response.getWarehouseName()).isEqualTo("Main");
    }

    @Test
    @DisplayName("an issued or accepted quote becomes a draft invoice, once")
    void convertsQuoteToInvoice() {
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ACCEPTED);
        assertThat(service.convertToInvoice(5L).getType()).isEqualTo(SalesDocumentType.INVOICE);

        when(documentRepository.existsBySourceIdAndCompanyIdAndStatusNot(
                5L, COMPANY_ID, SalesDocumentStatus.CANCELLED)).thenReturn(true);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already has a sales order or an invoice");

        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.REJECTED);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("issued or accepted quote");
    }

    @Test
    @DisplayName("what can be invoiced: not an unconfirmed order, an order with delivery notes, an undelivered note, an invoice")
    void invoiceConversionRules() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Confirm the sales order");

        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED)));
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("invoice them instead");

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.INVOICE, SalesDocumentStatus.PAID)));
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already invoiced");

        existing(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.ISSUED);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("delivered delivery note");

        existing(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be turned into an invoice");
    }

    @Test
    @DisplayName("an order that has been invoiced cannot also go out on a delivery note")
    void invoicedOrderCannotBeDelivered() {
        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED)));

        assertThatThrownBy(() -> service.convertToDeliveryNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already invoiced");
    }

    @Test
    @DisplayName("only an issued invoice can receive payments, and a paid one no more")
    void payableInvoice() {
        invoice(SalesDocumentStatus.ISSUED, null);
        assertThat(service.getPayableInvoice(5L).getId()).isEqualTo(5L);

        invoice(SalesDocumentStatus.PARTIALLY_PAID, null);
        assertThat(service.getPayableInvoice(5L)).isNotNull();

        invoice(SalesDocumentStatus.DRAFT, null);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("Issue the invoice");
        invoice(SalesDocumentStatus.CANCELLED, null);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("cancelled");
        invoice(SalesDocumentStatus.PAID, null);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("paid in full");
        existing(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("Only an invoice");
    }

    @Test
    @DisplayName("applyPaid rewrites the paid amount and the status of the invoice")
    void applyPaidOnInvoice() {
        SalesDocument invoice = invoice(SalesDocumentStatus.ISSUED, null);

        service.applyPaid(5L, invoice.getTotal());

        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PAID);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo(invoice.getTotal());
    }

    // ----- Credit notes -----

    private SalesDocument sourceInvoice(SalesDocumentStatus status, String total) {
        SalesDocument invoice = new SalesDocument();
        invoice.setId(9L);
        invoice.setType(SalesDocumentType.INVOICE);
        invoice.setStatus(status);
        invoice.setReference("INV-2026-00001");
        invoice.setTotal(new BigDecimal(total));
        return invoice;
    }

    private SalesDocument creditNote(SalesDocumentStatus status, SalesDocument invoice) {
        SalesDocument note = existing(SalesDocumentType.CREDIT_NOTE, status); // total 35.700
        note.setSource(invoice);
        return note;
    }

    private void creditedOnInvoice(String amount) {
        when(documentRepository.sumByInvoice(COMPANY_ID, 9L, SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.ISSUED))
                .thenReturn(new BigDecimal(amount));
    }

    @Test
    @DisplayName("a credit note is not created on its own: it is made from an invoice")
    void creditNoteIsNotCreatedByHand() {
        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.CREDIT_NOTE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("made from an invoice");
        verify(documentRepository, never()).save(any(SalesDocument.class));
    }

    @Test
    @DisplayName("a draft credit note keeps the customer of its invoice")
    void creditNoteKeepsItsCustomer() {
        creditNote(SalesDocumentStatus.DRAFT, sourceInvoice(SalesDocumentStatus.ISSUED, "100.000"));
        Customer other = new Customer();
        other.setId(4L);
        lenient().when(customerService.getAssignable(4L)).thenReturn(other);
        SalesDocumentRequest request = request(SalesDocumentType.CREDIT_NOTE, freeLine("Item", "1", "10.000", null));
        request.setCustomerId(4L);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("customer of its invoice");

        request.setCustomerId(3L);
        assertThat(service.update(5L, request).getCustomerId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("an issued invoice - paid or not - becomes a draft credit note with the same lines, moving no stock")
    void convertsInvoiceToCreditNote() {
        for (SalesDocumentStatus status : new SalesDocumentStatus[]{
                SalesDocumentStatus.ISSUED, SalesDocumentStatus.PARTIALLY_PAID, SalesDocumentStatus.PAID}) {
            SalesDocument invoice = existing(SalesDocumentType.INVOICE, status);
            invoice.setReference("INV-2026-00001");

            SalesDocumentResponse response = service.convertToCreditNote(5L);

            assertThat(response.getType()).isEqualTo(SalesDocumentType.CREDIT_NOTE);
            assertThat(response.getStatus()).isEqualTo(SalesDocumentStatus.DRAFT);
            assertThat(response.getSourceReference()).isEqualTo("INV-2026-00001");
            assertThat(response.getSourceType()).isEqualTo(SalesDocumentType.INVOICE);
            assertThat(response.getWarehouseId()).isNull();
            assertThat(response.getTotal()).isEqualByComparingTo(invoice.getTotal());
            assertThat(response.getLines()).hasSize(1);
        }
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("what can be credited: an issued invoice that is not credited in full already")
    void creditNoteConversionRules() {
        existing(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only an issued invoice");

        existing(SalesDocumentType.INVOICE, SalesDocumentStatus.CANCELLED);
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only an issued invoice");

        existing(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED);
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only an invoice can be credited");

        SalesDocument credited = existing(SalesDocumentType.INVOICE, SalesDocumentStatus.PAID);
        credited.setCreditedAmount(credited.getTotal());
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("credited in full");
    }

    @Test
    @DisplayName("issuing a credit note numbers it and takes its total off its invoice, without touching the stock")
    void issueCreditNote() {
        SalesDocument invoice = sourceInvoice(SalesDocumentStatus.ISSUED, "100.000");
        SalesDocument note = creditNote(SalesDocumentStatus.DRAFT, invoice);
        when(numberingService.allocate(DocumentType.SALES_CREDIT_NOTE)).thenReturn("CN-2026-00001");
        creditedOnInvoice("35.700");

        SalesDocumentResponse response = service.issue(5L);

        assertThat(response.getReference()).isEqualTo("CN-2026-00001");
        assertThat(note.getStatus()).isEqualTo(SalesDocumentStatus.ISSUED);
        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("35.700");
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PARTIALLY_PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("64.300");
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("a credit note that settles the invoice moves it to paid")
    void creditNoteSettlesTheInvoice() {
        SalesDocument invoice = sourceInvoice(SalesDocumentStatus.ISSUED, "35.700");
        creditNote(SalesDocumentStatus.DRAFT, invoice);
        when(numberingService.allocate(DocumentType.SALES_CREDIT_NOTE)).thenReturn("CN-2026-00001");
        creditedOnInvoice("35.700");

        service.issue(5L);

        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PAID);
    }

    @Test
    @DisplayName("credit notes cannot take more off an invoice than it is worth")
    void creditNoteCannotExceedTheInvoice() {
        SalesDocument invoice = sourceInvoice(SalesDocumentStatus.PARTIALLY_PAID, "100.000");
        creditNote(SalesDocumentStatus.DRAFT, invoice);
        when(numberingService.allocate(DocumentType.SALES_CREDIT_NOTE)).thenReturn("CN-2026-00002");
        creditedOnInvoice("110.000"); // 74.3 credited before, this one 35.7 on top

        assertThatThrownBy(() -> service.issue(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This credit note is more than the 25.700 that can still be credited on INV-2026-00001");
    }

    @Test
    @DisplayName("a credit note cannot be issued on an invoice that was cancelled meanwhile")
    void creditNoteOnCancelledInvoice() {
        creditNote(SalesDocumentStatus.DRAFT, sourceInvoice(SalesDocumentStatus.CANCELLED, "100.000"));
        when(numberingService.allocate(DocumentType.SALES_CREDIT_NOTE)).thenReturn("CN-2026-00001");

        assertThatThrownBy(() -> service.issue(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only an issued invoice");
    }

    @Test
    @DisplayName("cancelling a credit note gives the invoice back what it took off")
    void cancelCreditNote() {
        SalesDocument invoice = sourceInvoice(SalesDocumentStatus.PARTIALLY_PAID, "100.000");
        invoice.setCreditedAmount(new BigDecimal("35.700"));
        SalesDocument note = creditNote(SalesDocumentStatus.ISSUED, invoice);
        creditedOnInvoice("0");

        service.cancel(5L);

        assertThat(note.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.ISSUED);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an invoice with a live credit note cannot be cancelled until the credit note is")
    void invoiceWithCreditNoteCannotBeCancelled() {
        existing(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has credit notes");
        verifyNoInteractions(stockService);
    }

    // ----- Return notes -----

    private SalesDocument returnNote(SalesDocumentStatus status, SalesDocument source) {
        SalesDocument note = existing(SalesDocumentType.RETURN_NOTE, status);
        note.setWarehouse(warehouse());
        note.setSource(source);
        addLine(note, good(20L, ProductKind.GOOD), "2");
        addLine(note, good(21L, ProductKind.SERVICE), "1");
        return note;
    }

    @Test
    @DisplayName("a return note needs a warehouse, and can be made by hand")
    void returnNoteNeedsWarehouse() {
        assertThatThrownBy(() -> service.create(
                request(SalesDocumentType.RETURN_NOTE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A return note needs a warehouse");

        SalesDocumentRequest request = request(SalesDocumentType.RETURN_NOTE, freeLine("Item", "1", "10.000", null));
        request.setWarehouseId(1L);
        assertThat(service.create(request).getWarehouseName()).isEqualTo("Main");
    }

    @Test
    @DisplayName("issuing a return note numbers it, then puts its goods - and only its goods - back into the warehouse")
    void issueReturnNoteBringsGoodsBack() {
        SalesDocument note = returnNote(SalesDocumentStatus.DRAFT, null);
        when(numberingService.allocate(DocumentType.RETURN_NOTE)).thenReturn("RN-2026-00001");

        SalesDocumentResponse response = service.issue(5L);

        assertThat(response.getReference()).isEqualTo("RN-2026-00001");
        assertThat(note.getStatus()).isEqualTo(SalesDocumentStatus.ISSUED);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<StockRequirement>> goods = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stockService).receive(eq(StockSource.SALES_DOCUMENT), eq(5L), eq(note.getWarehouse()), goods.capture());
        assertThat(goods.getValue()).extracting(g -> g.product().getId()).containsExactly(20L);
        verify(stockService, never()).deliver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelling an issued return note takes its goods out again, and a refusal by the stock leaves it issued")
    void cancelReturnNote() {
        SalesDocument note = returnNote(SalesDocumentStatus.ISSUED, null);

        service.cancel(5L);

        assertThat(note.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);
        verify(stockService).reverseReceipt(StockSource.SALES_DOCUMENT, 5L);
        verify(stockService, never()).undoDelivery(any(), any(), anyBoolean());

        returnNote(SalesDocumentStatus.ISSUED, null);
        doThrow(new BusinessRuleException("Not enough stock")).when(stockService).reverseReceipt(any(), any());
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Not enough stock");
    }

    @Test
    @DisplayName("a delivery note or an invoice with a live return note cannot be cancelled, and one with a dead return note can")
    void documentWithReturnNoteCannotBeCancelled() {
        existing(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.RETURN_NOTE, SalesDocumentStatus.ISSUED)));
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has a return note");

        existing(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED);
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has a return note");
        verify(stockService, never()).undoDelivery(any(), any(), anyBoolean());

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(child(SalesDocumentType.RETURN_NOTE, SalesDocumentStatus.CANCELLED)));
        service.cancel(5L);
    }

    @Test
    @DisplayName("a delivered note becomes a draft return note into the warehouse it left, with the same lines")
    void convertsDeliveredNoteToReturnNote() {
        SalesDocument delivery = existing(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED);
        delivery.setReference("BL-2026-00001");
        delivery.setWarehouse(warehouse());

        SalesDocumentResponse response = service.convertToReturnNote(5L);

        assertThat(response.getType()).isEqualTo(SalesDocumentType.RETURN_NOTE);
        assertThat(response.getStatus()).isEqualTo(SalesDocumentStatus.DRAFT);
        assertThat(response.getSourceReference()).isEqualTo("BL-2026-00001");
        assertThat(response.getSourceType()).isEqualTo(SalesDocumentType.DELIVERY_NOTE);
        assertThat(response.getWarehouseName()).isEqualTo("Main");
        assertThat(response.getLines()).hasSize(1);
        verifyNoInteractions(stockService); // a draft moves nothing
    }

    @Test
    @DisplayName("an issued invoice - paid or not - becomes a draft return note")
    void convertsInvoiceToReturnNote() {
        for (SalesDocumentStatus status : new SalesDocumentStatus[]{
                SalesDocumentStatus.ISSUED, SalesDocumentStatus.PARTIALLY_PAID, SalesDocumentStatus.PAID}) {
            SalesDocument invoice = existing(SalesDocumentType.INVOICE, status);
            invoice.setWarehouse(warehouse());

            SalesDocumentResponse response = service.convertToReturnNote(5L);

            assertThat(response.getType()).isEqualTo(SalesDocumentType.RETURN_NOTE);
            assertThat(response.getSourceType()).isEqualTo(SalesDocumentType.INVOICE);
        }
    }

    @Test
    @DisplayName("what can be returned: a delivered note or a standing invoice, not an undelivered note, a draft, a cancelled invoice or an order")
    void returnNoteConversionRules() {
        existing(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.ISSUED);
        assertThatThrownBy(() -> service.convertToReturnNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("delivered delivery note");

        existing(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToReturnNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only an issued invoice");

        existing(SalesDocumentType.INVOICE, SalesDocumentStatus.CANCELLED);
        assertThatThrownBy(() -> service.convertToReturnNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only an issued invoice");

        existing(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        assertThatThrownBy(() -> service.convertToReturnNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("delivery note or an invoice");
    }

    @Test
    @DisplayName("a return note made from a document keeps its customer, one made by hand is free")
    void returnNoteKeepsItsCustomer() {
        returnNote(SalesDocumentStatus.DRAFT, deliveredNoteSource());
        Customer other = new Customer();
        other.setId(4L);
        lenient().when(customerService.getAssignable(4L)).thenReturn(other);
        SalesDocumentRequest request = request(SalesDocumentType.RETURN_NOTE, freeLine("Item", "1", "10.000", null));
        request.setCustomerId(4L);
        request.setWarehouseId(1L);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("customer of the document");

        returnNote(SalesDocumentStatus.DRAFT, null);
        assertThat(service.update(5L, request).getCustomerId()).isEqualTo(4L);
    }

    private SalesDocument deliveredNoteSource() {
        SalesDocument note = new SalesDocument();
        note.setId(9L);
        note.setType(SalesDocumentType.DELIVERY_NOTE);
        note.setStatus(SalesDocumentStatus.DELIVERED);
        return note;
    }

    // ----- Dashboard figures -----

    @Test
    @DisplayName("the sales figures are net of credit notes, cover the last six months oldest first, and list five late invoices at most")
    void salesFigures() {
        when(documentRepository.sumTotal(eq(COMPANY_ID), eq(SalesDocumentType.INVOICE), any(), any(), any()))
                .thenReturn(new BigDecimal("100.000"));
        when(documentRepository.sumTotal(eq(COMPANY_ID), eq(SalesDocumentType.CREDIT_NOTE), any(), any(), any()))
                .thenReturn(new BigDecimal("30.000"));
        when(documentRepository.unpaid(eq(COMPANY_ID), eq(SalesDocumentType.INVOICE), any()))
                .thenReturn(new com.sales.smartBusiness.common.AmountSummary(3L, new BigDecimal("140.000")));
        when(documentRepository.overdue(eq(COMPANY_ID), eq(SalesDocumentType.INVOICE), any(), any()))
                .thenReturn(new com.sales.smartBusiness.common.AmountSummary(1L, new BigDecimal("30.000")));
        SalesDocument late = new SalesDocument();
        late.setType(SalesDocumentType.INVOICE);
        late.setCustomer(customer);
        when(documentRepository.overdueInvoices(eq(COMPANY_ID), eq(SalesDocumentType.INVOICE), any(), any(), any()))
                .thenReturn(List.of(late));

        SalesFigures figures = service.figures(LocalDate.of(2026, 9, 20));

        assertThat(figures.today()).isEqualByComparingTo("70.000"); // 100 invoiced - 30 credited
        assertThat(figures.month()).isEqualByComparingTo("70.000");
        assertThat(figures.months()).extracting(m -> m.month())
                .containsExactly("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09");
        assertThat(figures.unpaid().count()).isEqualTo(3L);
        assertThat(figures.overdue().amount()).isEqualByComparingTo("30.000");
        assertThat(figures.overdueInvoices()).hasSize(1);
        verify(documentRepository).overdueInvoices(eq(COMPANY_ID), eq(SalesDocumentType.INVOICE),
                eq(List.of(SalesDocumentStatus.ISSUED, SalesDocumentStatus.PARTIALLY_PAID)), eq(LocalDate.of(2026, 9, 20)),
                eq(org.springframework.data.domain.PageRequest.of(0, 5)));
    }

    @Test
    @DisplayName("only issued, partly paid and paid invoices count as revenue, and only issued credit notes are taken off it")
    void salesFiguresCountTheRightStatuses() {
        when(documentRepository.sumTotal(any(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        service.figures(LocalDate.of(2026, 9, 20));

        verify(documentRepository, atLeastOnce()).sumTotal(eq(COMPANY_ID), eq(SalesDocumentType.INVOICE),
                eq(List.of(SalesDocumentStatus.ISSUED, SalesDocumentStatus.PARTIALLY_PAID, SalesDocumentStatus.PAID)), any(), any());
        verify(documentRepository, atLeastOnce()).sumTotal(eq(COMPANY_ID), eq(SalesDocumentType.CREDIT_NOTE),
                eq(List.of(SalesDocumentStatus.ISSUED)), any(), any());
    }
}
