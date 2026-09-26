package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.DocumentType;
import com.sales.smartBusiness.numbering.NumberingService;
import com.sales.smartBusiness.product.Product;
import com.sales.smartBusiness.product.ProductKind;
import com.sales.smartBusiness.product.ProductService;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.stock.StockRequirement;
import com.sales.smartBusiness.stock.StockService;
import com.sales.smartBusiness.stock.StockSource;
import com.sales.smartBusiness.supplier.Supplier;
import com.sales.smartBusiness.supplier.SupplierService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseDocumentServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private PurchaseDocumentRepository documentRepository;
    @Mock private CompanyService companyService;
    @Mock private SupplierService supplierService;
    @Mock private ProductService productService;
    @Mock private TaxService taxService;
    @Mock private NumberingService numberingService;
    @Mock private WarehouseService warehouseService;
    @Mock private StockService stockService;
    @Mock private CurrentUser currentUser;
    @Spy private PurchaseDocumentMapper documentMapper = Mappers.getMapper(PurchaseDocumentMapper.class);

    @InjectMocks private PurchaseDocumentService service;

    private Company company;
    private Supplier supplier;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        company = new Company();
        supplier = new Supplier();
        supplier.setId(3L);
        supplier.setName("Supplier Alpha");
        warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setName("Main");

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(companyService.currentReference()).thenReturn(company);
        lenient().when(supplierService.getAssignable(3L)).thenReturn(supplier);
        lenient().when(warehouseService.getAssignable(1L)).thenReturn(warehouse);
        lenient().when(warehouseService.getDefault()).thenReturn(warehouse);
        lenient().when(productService.resolvePurchasable(any())).thenReturn(Map.of());
        lenient().when(taxService.resolveAssignable(any())).thenReturn(Set.of());
        lenient().when(documentRepository.save(any(PurchaseDocument.class))).thenAnswer(call -> {
            PurchaseDocument saved = call.getArgument(0);
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

    private static PurchaseDocumentLineRequest freeLine(String designation, String quantity, String price, Long vatTaxId) {
        PurchaseDocumentLineRequest line = new PurchaseDocumentLineRequest();
        line.setDesignation(designation);
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(price));
        line.setVatTaxId(vatTaxId);
        return line;
    }

    private static PurchaseDocumentRequest request(PurchaseDocumentType type, PurchaseDocumentLineRequest... lines) {
        PurchaseDocumentRequest request = new PurchaseDocumentRequest();
        request.setType(type);
        request.setSupplierId(3L);
        request.setIssueDate(LocalDate.of(2026, 9, 20));
        request.setLines(List.of(lines));
        return request;
    }

    private static Product good(long id, ProductKind kind) {
        Product product = new Product();
        product.setId(id);
        product.setName("Item " + id);
        product.setKind(kind);
        return product;
    }

    private PurchaseDocument existing(PurchaseDocumentType type, PurchaseDocumentStatus status, Product... products) {
        PurchaseDocument document = new PurchaseDocument();
        document.setId(5L);
        document.setCompany(company);
        document.setSupplier(supplier);
        document.setWarehouse(type == PurchaseDocumentType.GOODS_RECEIPT ? warehouse : null);
        document.setType(type);
        document.setStatus(status);
        document.setIssueDate(LocalDate.of(2026, 9, 1));

        for (Product product : products) {
            PurchaseDocumentLine line = new PurchaseDocumentLine();
            line.setProduct(product);
            line.setDesignation(product == null ? "Free" : product.getName());
            line.setQuantity(new BigDecimal("3"));
            line.setUnitPrice(new BigDecimal("10.000"));
            line.setDiscountRate(BigDecimal.ZERO);
            line.setVatRate(new BigDecimal("19"));
            document.addLine(line);
        }
        if (products.length == 0) {
            PurchaseDocumentLine line = new PurchaseDocumentLine();
            line.setDesignation("Free");
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(new BigDecimal("30.000"));
            line.setDiscountRate(BigDecimal.ZERO);
            line.setVatRate(new BigDecimal("19"));
            document.addLine(line);
        }
        document.recalculate();

        when(documentRepository.findByIdAndCompanyId(5L, COMPANY_ID)).thenReturn(Optional.of(document));
        return document;
    }

    // ----- Create -----

    @Test
    @DisplayName("create builds a draft with computed totals, no number and no stock effect")
    void createsDraft() {
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(
                tax(10L, "TVA 19%", TaxKind.VAT_RATE, "19", null, false),
                tax(11L, "FODEC", TaxKind.PERCENTAGE_SURCHARGE, "1", null, true),
                tax(12L, "Timbre fiscal", TaxKind.FIXED_PER_DOCUMENT, null, "1.000", false)));
        PurchaseDocumentRequest request = request(PurchaseDocumentType.PURCHASE_ORDER,
                freeLine("Fabric", "1", "30.000", 10L));
        request.setTaxIds(Set.of(11L, 12L));

        PurchaseDocumentResponse response = service.create(request);

        assertThat(response.getStatus()).isEqualTo(PurchaseDocumentStatus.DRAFT);
        assertThat(response.getReference()).isNull();
        assertThat(response.getSupplierName()).isEqualTo("Supplier Alpha");
        assertThat(response.getTotal()).isEqualByComparingTo("37.057");
        verifyNoInteractions(numberingService, stockService);
    }

    @Test
    @DisplayName("a product line takes the product's purchase price, name and code when left blank")
    void productLineTakesPurchasePrice() {
        Product product = good(20L, ProductKind.GOOD);
        product.setReference("P-0020");
        product.setPurchasePrice(new BigDecimal("7.500"));
        product.setSalePrice(new BigDecimal("12.500"));
        when(productService.resolvePurchasable(Set.of(20L))).thenReturn(Map.of(20L, product));

        PurchaseDocumentLineRequest line = new PurchaseDocumentLineRequest();
        line.setProductId(20L);
        line.setQuantity(new BigDecimal("2"));

        PurchaseDocumentResponse response = service.create(request(PurchaseDocumentType.PURCHASE_ORDER, line));

        PurchaseDocumentLineResponse created = response.getLines().get(0);
        assertThat(created.getDesignation()).isEqualTo("Item 20");
        assertThat(created.getReference()).isEqualTo("P-0020");
        assertThat(created.getUnitPrice()).isEqualByComparingTo("7.500");
        assertThat(created.getLineTotal()).isEqualByComparingTo("15.000");
    }

    @Test
    @DisplayName("a sale-only product is refused, through the product service")
    void saleOnlyProductRefused() {
        when(productService.resolvePurchasable(any()))
                .thenThrow(new BusinessRuleException("\"Cable\" is a sale-only product and cannot be purchased"));
        PurchaseDocumentLineRequest line = new PurchaseDocumentLineRequest();
        line.setProductId(20L);
        line.setQuantity(BigDecimal.ONE);

        assertThatThrownBy(() -> service.create(request(PurchaseDocumentType.PURCHASE_ORDER, line)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sale-only");

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("the supplier is resolved through the supplier service, so a foreign one is refused")
    void foreignSupplierRefused() {
        when(supplierService.getAssignable(3L)).thenThrow(new BusinessRuleException("The selected supplier does not exist"));

        assertThatThrownBy(() -> service.create(
                request(PurchaseDocumentType.PURCHASE_ORDER, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class);

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a goods receipt needs a warehouse; an order gets none even if one is sent")
    void warehouseRules() {
        assertThatThrownBy(() -> service.create(
                request(PurchaseDocumentType.GOODS_RECEIPT, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("needs a warehouse");

        PurchaseDocumentRequest receipt = request(PurchaseDocumentType.GOODS_RECEIPT, freeLine("Item", "1", "10.000", null));
        receipt.setWarehouseId(1L);
        assertThat(service.create(receipt).getWarehouseName()).isEqualTo("Main");

        PurchaseDocumentRequest order = request(PurchaseDocumentType.PURCHASE_ORDER, freeLine("Item", "1", "10.000", null));
        order.setWarehouseId(1L);
        assertThat(service.create(order).getWarehouseId()).isNull();
    }

    @Test
    @DisplayName("a VAT picked on a line must be a VAT rate, and a taxes of the whole document must not be")
    void taxKindsChecked() {
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(
                tax(11L, "FODEC", TaxKind.PERCENTAGE_SURCHARGE, "1", null, true)));
        assertThatThrownBy(() -> service.create(
                request(PurchaseDocumentType.PURCHASE_ORDER, freeLine("Item", "1", "10.000", 11L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not a VAT rate");

        when(taxService.resolveAssignable(any())).thenReturn(Set.of(tax(10L, "TVA 19%", TaxKind.VAT_RATE, "19", null, false)));
        PurchaseDocumentRequest wrong = request(PurchaseDocumentType.PURCHASE_ORDER, freeLine("Item", "1", "10.000", null));
        wrong.setTaxIds(Set.of(10L));
        assertThatThrownBy(() -> service.create(wrong))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("VAT rate");
    }

    // ----- Update / delete -----

    @Test
    @DisplayName("update replaces the lines and recomputes")
    void updateReplacesLines() {
        PurchaseDocument draft = existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);

        PurchaseDocumentResponse response = service.update(5L, request(PurchaseDocumentType.PURCHASE_ORDER,
                freeLine("A", "2", "10.000", null), freeLine("B", "1", "5.000", null)));

        assertThat(draft.getLines()).hasSize(2);
        assertThat(response.getSubtotal()).isEqualByComparingTo("25.000");
    }

    @Test
    @DisplayName("a validated document can no longer be edited or deleted, and its type cannot change")
    void validatedIsFrozen() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);

        assertThatThrownBy(() -> service.update(5L,
                request(PurchaseDocumentType.PURCHASE_ORDER, freeLine("A", "1", "1.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a draft");
        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(BusinessRuleException.class);
        verify(documentRepository, never()).delete(any());

        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.update(5L,
                request(PurchaseDocumentType.GOODS_RECEIPT, freeLine("A", "1", "1.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("type");
    }

    @Test
    @DisplayName("a draft is deleted, and an unknown or foreign document is a 404")
    void deleteAndUnknown() {
        PurchaseDocument draft = existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);
        service.delete(5L);
        verify(documentRepository).delete(draft);

        when(documentRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.validate(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- Validate -----

    @Test
    @DisplayName("validating a purchase order numbers it and touches no stock")
    void validateOrder() {
        PurchaseDocument order = existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);
        when(numberingService.allocate(DocumentType.PURCHASE_ORDER)).thenReturn("PO-2026-00001");

        PurchaseDocumentResponse response = service.validate(5L);

        assertThat(order.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
        assertThat(response.getReference()).isEqualTo("PO-2026-00001");
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("validating a goods receipt puts its goods, and only its goods, into its warehouse")
    void validateReceiptReceivesGoods() {
        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.DRAFT,
                good(20L, ProductKind.GOOD), good(21L, ProductKind.SERVICE), null);
        when(numberingService.allocate(DocumentType.GOODS_RECEIPT)).thenReturn("GR-2026-00001");

        PurchaseDocumentResponse response = service.validate(5L);

        assertThat(response.getReference()).isEqualTo("GR-2026-00001");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockRequirement>> goods = ArgumentCaptor.forClass(List.class);
        verify(stockService).receive(eq(StockSource.PURCHASE_DOCUMENT), eq(5L), eq(warehouse), goods.capture());
        assertThat(goods.getValue()).extracting(g -> g.product().getId()).containsExactly(20L);
        assertThat(goods.getValue().get(0).quantity()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("validating skips a number already taken, and cannot be done twice")
    void validateRules() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);
        when(numberingService.allocate(DocumentType.PURCHASE_ORDER)).thenReturn("PO-1", "PO-2");
        when(documentRepository.existsByCompanyIdAndTypeAndReferenceIgnoreCase(
                COMPANY_ID, PurchaseDocumentType.PURCHASE_ORDER, "PO-1")).thenReturn(true);
        assertThat(service.validate(5L).getReference()).isEqualTo("PO-2");

        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.validate(5L)).isInstanceOf(BusinessRuleException.class);
    }

    // ----- Cancel -----

    @Test
    @DisplayName("cancelling a validated receipt takes its goods back out of the stock")
    void cancelReceiptReversesStock() {
        PurchaseDocument receipt = existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);

        service.cancel(5L);

        assertThat(receipt.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);
        verify(stockService).reverseReceipt(StockSource.PURCHASE_DOCUMENT, 5L);
    }

    @Test
    @DisplayName("a receipt whose goods cannot be taken back stays validated")
    void cancelReceiptRefusedByStock() {
        PurchaseDocument receipt = existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        doThrow(new BusinessRuleException("Not enough stock")).when(stockService).reverseReceipt(any(), any());

        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("cancelling an order touches no stock")
    void cancelOrder() {
        PurchaseDocument order = existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);

        service.cancel(5L);

        assertThat(order.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an order with a live receipt cannot be cancelled, but one whose receipts are cancelled can")
    void cancelOrderWithReceipts() {
        PurchaseDocument order = existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        PurchaseDocument live = new PurchaseDocument();
        live.setType(PurchaseDocumentType.GOODS_RECEIPT);
        live.setStatus(PurchaseDocumentStatus.VALIDATED);
        PurchaseDocument dead = new PurchaseDocument();
        dead.setType(PurchaseDocumentType.GOODS_RECEIPT);
        dead.setStatus(PurchaseDocumentStatus.CANCELLED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID))
                .thenReturn(List.of(dead, live));

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("goods receipts");

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(dead));
        service.cancel(5L);
        assertThat(order.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);
    }

    @Test
    @DisplayName("a draft is deleted rather than cancelled, and a cancelled document stays cancelled")
    void cancelRules() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(BusinessRuleException.class);

        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(5L)).isInstanceOf(BusinessRuleException.class);
    }

    // ----- Convert -----

    @Test
    @DisplayName("a validated order becomes a draft receipt in the default warehouse, with the same lines and total")
    void convertsOrder() {
        PurchaseDocument order = existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED,
                good(20L, ProductKind.GOOD));
        order.setReference("PO-2026-00001");

        PurchaseDocumentResponse response = service.convertToReceipt(5L);

        assertThat(response.getType()).isEqualTo(PurchaseDocumentType.GOODS_RECEIPT);
        assertThat(response.getStatus()).isEqualTo(PurchaseDocumentStatus.DRAFT);
        assertThat(response.getSourceId()).isEqualTo(5L);
        assertThat(response.getSourceReference()).isEqualTo("PO-2026-00001");
        assertThat(response.getWarehouseName()).isEqualTo("Main");
        assertThat(response.getSupplierId()).isEqualTo(3L);
        assertThat(response.getTotal()).isEqualByComparingTo(order.getTotal());
        assertThat(response.getLines()).hasSize(1);
        verifyNoInteractions(stockService); // a draft receipt moves nothing
    }

    @Test
    @DisplayName("an order can be received in several parts: nothing stops a second receipt")
    void severalReceipts() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);

        service.convertToReceipt(5L);
        service.convertToReceipt(5L);

        verify(documentRepository, times(2)).save(any(PurchaseDocument.class));
    }

    @Test
    @DisplayName("only a validated purchase order can be converted")
    void convertRules() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToReceipt(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Validate");

        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.CANCELLED);
        assertThatThrownBy(() -> service.convertToReceipt(5L)).isInstanceOf(BusinessRuleException.class);

        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.convertToReceipt(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a purchase order");
    }

    // ----- Preview -----

    @Test
    @DisplayName("preview returns the totals without saving anything")
    void previewSavesNothing() {
        when(taxService.resolveAssignable(any())).thenReturn(Set.of(tax(10L, "TVA 19%", TaxKind.VAT_RATE, "19", null, false)));

        PurchaseDocumentResponse response = service.preview(
                request(PurchaseDocumentType.PURCHASE_ORDER, freeLine("Item", "1", "100.000", 10L)));

        assertThat(response.getTotal()).isEqualByComparingTo("119.000");
        assertThat(response.getId()).isNull();
        verify(documentRepository, never()).save(any());
        verifyNoInteractions(numberingService, stockService);
    }

    // ----- Invoices -----

    private PurchaseDocument invoice(PurchaseDocumentStatus status, PurchaseDocument source) {
        PurchaseDocument invoice = existing(PurchaseDocumentType.PURCHASE_INVOICE, status,
                good(20L, ProductKind.GOOD), good(21L, ProductKind.SERVICE));
        invoice.setWarehouse(warehouse);
        invoice.setSource(source);
        return invoice;
    }

    private static PurchaseDocument other(PurchaseDocumentType type, PurchaseDocumentStatus status) {
        PurchaseDocument document = new PurchaseDocument();
        document.setId(9L);
        document.setType(type);
        document.setStatus(status);
        return document;
    }

    @Test
    @DisplayName("an invoice needs a warehouse, like a goods receipt")
    void invoiceNeedsWarehouse() {
        assertThatThrownBy(() -> service.create(
                request(PurchaseDocumentType.PURCHASE_INVOICE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A purchase invoice needs a warehouse");

        PurchaseDocumentRequest request = request(PurchaseDocumentType.PURCHASE_INVOICE, freeLine("Item", "1", "10.000", null));
        request.setWarehouseId(1L);
        assertThat(service.create(request).getWarehouseName()).isEqualTo("Main");
    }

    @Test
    @DisplayName("validating an invoice made by hand numbers it, then brings its goods - and only its goods - into the stock")
    void validateInvoiceBringsGoodsIn() {
        PurchaseDocument invoice = invoice(PurchaseDocumentStatus.DRAFT, null);
        when(numberingService.allocate(DocumentType.PURCHASE_INVOICE)).thenReturn("PINV-2026-00001");

        PurchaseDocumentResponse response = service.validate(5L);

        assertThat(response.getReference()).isEqualTo("PINV-2026-00001");
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
        ArgumentCaptor<List<StockRequirement>> goods = ArgumentCaptor.forClass(List.class);
        verify(stockService).receive(eq(StockSource.PURCHASE_DOCUMENT), eq(5L), eq(warehouse), goods.capture());
        assertThat(goods.getValue()).extracting(g -> g.product().getId()).containsExactly(20L);
    }

    @Test
    @DisplayName("an invoice made from a purchase order brings the goods in too: nothing else did")
    void validateInvoiceFromOrder() {
        invoice(PurchaseDocumentStatus.DRAFT, other(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED));
        when(numberingService.allocate(DocumentType.PURCHASE_INVOICE)).thenReturn("PINV-2026-00001");

        service.validate(5L);

        verify(stockService).receive(eq(StockSource.PURCHASE_DOCUMENT), eq(5L), eq(warehouse), any());
    }

    @Test
    @DisplayName("an invoice made from a goods receipt moves no stock: the receipt already brought the goods in")
    void validateInvoiceFromReceipt() {
        invoice(PurchaseDocumentStatus.DRAFT, other(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED));
        when(numberingService.allocate(DocumentType.PURCHASE_INVOICE)).thenReturn("PINV-2026-00001");

        service.validate(5L);

        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("cancelling an unpaid invoice takes its goods back out, unless a receipt brought them")
    void cancelInvoice() {
        PurchaseDocument invoice = invoice(PurchaseDocumentStatus.VALIDATED, null);

        service.cancel(5L);

        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);
        verify(stockService).reverseReceipt(StockSource.PURCHASE_DOCUMENT, 5L);

        reset(stockService);
        invoice(PurchaseDocumentStatus.VALIDATED, other(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED));
        service.cancel(5L);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an invoice whose goods have been sold since stays validated when the stock refuses to give them back")
    void cancelInvoiceRefusedByStock() {
        invoice(PurchaseDocumentStatus.VALIDATED, null);
        doThrow(new BusinessRuleException("Not enough stock")).when(stockService).reverseReceipt(any(), any());

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Not enough stock");
    }

    @Test
    @DisplayName("an invoice with payments cannot be cancelled until they are")
    void invoiceWithPaymentsCannotBeCancelled() {
        PurchaseDocument invoice = invoice(PurchaseDocumentStatus.PARTIALLY_PAID, null);
        invoice.setPaidAmount(new BigDecimal("5.000"));

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has payments");
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an order or a receipt with a live invoice cannot be cancelled, and one with a dead invoice can")
    void invoicedDocumentCannotBeCancelled() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED)));
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has an invoice");

        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has an invoice");

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.CANCELLED)));
        service.cancel(5L);
    }

    @Test
    @DisplayName("a validated goods receipt becomes a draft invoice into the warehouse it went to, with the same lines")
    void convertsReceiptToInvoice() {
        PurchaseDocument receipt = existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        receipt.setReference("GR-2026-00001");
        Warehouse sfax = new Warehouse();
        sfax.setId(2L);
        sfax.setName("Sfax");
        receipt.setWarehouse(sfax);

        PurchaseDocumentResponse response = service.convertToInvoice(5L);

        assertThat(response.getType()).isEqualTo(PurchaseDocumentType.PURCHASE_INVOICE);
        assertThat(response.getStatus()).isEqualTo(PurchaseDocumentStatus.DRAFT);
        assertThat(response.getSourceReference()).isEqualTo("GR-2026-00001");
        assertThat(response.getSourceType()).isEqualTo(PurchaseDocumentType.GOODS_RECEIPT);
        assertThat(response.getWarehouseName()).isEqualTo("Sfax");
        assertThat(response.getTotal()).isEqualByComparingTo(receipt.getTotal());
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("a validated purchase order becomes a draft invoice on the default warehouse")
    void convertsOrderToInvoice() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);

        PurchaseDocumentResponse response = service.convertToInvoice(5L);

        assertThat(response.getType()).isEqualTo(PurchaseDocumentType.PURCHASE_INVOICE);
        assertThat(response.getWarehouseName()).isEqualTo("Main");
    }

    @Test
    @DisplayName("what can be invoiced: not a draft, an order with receipts, a document already invoiced, an invoice")
    void invoiceConversionRules() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Validate the purchase order first");

        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED)));
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("invoice them instead");

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.PAID)));
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already invoiced");

        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("validated goods receipt");

        existing(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.convertToInvoice(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be turned into an invoice");
    }

    @Test
    @DisplayName("an order that has been invoiced cannot also be received")
    void invoicedOrderCannotBeReceived() {
        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED)));

        assertThatThrownBy(() -> service.convertToReceipt(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already invoiced");
    }

    @Test
    @DisplayName("only a validated invoice can receive payments, and a paid one no more")
    void payableInvoice() {
        invoice(PurchaseDocumentStatus.VALIDATED, null);
        assertThat(service.getPayableInvoice(5L).getId()).isEqualTo(5L);

        invoice(PurchaseDocumentStatus.PARTIALLY_PAID, null);
        assertThat(service.getPayableInvoice(5L)).isNotNull();

        invoice(PurchaseDocumentStatus.DRAFT, null);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("Validate the invoice");
        invoice(PurchaseDocumentStatus.CANCELLED, null);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("cancelled");
        invoice(PurchaseDocumentStatus.PAID, null);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("paid in full");
        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.getPayableInvoice(5L)).hasMessageContaining("Only an invoice");
    }

    @Test
    @DisplayName("applyPaid rewrites the paid amount and the status of the invoice")
    void applyPaidOnInvoice() {
        PurchaseDocument invoice = invoice(PurchaseDocumentStatus.VALIDATED, null);

        service.applyPaid(5L, invoice.getTotal());

        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PAID);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo(invoice.getTotal());
    }

    // ----- Supplier credit notes -----

    private PurchaseDocument sourceInvoice(PurchaseDocumentStatus status, String total) {
        PurchaseDocument invoice = other(PurchaseDocumentType.PURCHASE_INVOICE, status);
        invoice.setReference("PINV-2026-00001");
        invoice.setTotal(new BigDecimal(total));
        return invoice;
    }

    private PurchaseDocument creditNote(PurchaseDocumentStatus status, PurchaseDocument invoice) {
        PurchaseDocument note = existing(PurchaseDocumentType.PURCHASE_CREDIT_NOTE, status); // total 35.700
        note.setSource(invoice);
        return note;
    }

    private void creditedOnInvoice(String amount) {
        when(documentRepository.sumByInvoice(COMPANY_ID, 9L, PurchaseDocumentType.PURCHASE_CREDIT_NOTE,
                PurchaseDocumentStatus.VALIDATED)).thenReturn(new BigDecimal(amount));
    }

    @Test
    @DisplayName("a supplier credit note is not created on its own: it is made from an invoice")
    void creditNoteIsNotCreatedByHand() {
        assertThatThrownBy(() -> service.create(
                request(PurchaseDocumentType.PURCHASE_CREDIT_NOTE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("made from an invoice");
        verify(documentRepository, never()).save(any(PurchaseDocument.class));
    }

    @Test
    @DisplayName("a draft credit note keeps the supplier of its invoice")
    void creditNoteKeepsItsSupplier() {
        creditNote(PurchaseDocumentStatus.DRAFT, sourceInvoice(PurchaseDocumentStatus.VALIDATED, "100.000"));
        Supplier otherSupplier = new Supplier();
        otherSupplier.setId(4L);
        lenient().when(supplierService.getAssignable(4L)).thenReturn(otherSupplier);
        PurchaseDocumentRequest request = request(PurchaseDocumentType.PURCHASE_CREDIT_NOTE, freeLine("Item", "1", "10.000", null));
        request.setSupplierId(4L);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("supplier of its invoice");

        request.setSupplierId(3L);
        assertThat(service.update(5L, request).getSupplierId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("a validated invoice - paid or not - becomes a draft credit note with the same lines, moving no stock")
    void convertsInvoiceToCreditNote() {
        for (PurchaseDocumentStatus status : new PurchaseDocumentStatus[]{
                PurchaseDocumentStatus.VALIDATED, PurchaseDocumentStatus.PARTIALLY_PAID, PurchaseDocumentStatus.PAID}) {
            PurchaseDocument invoice = existing(PurchaseDocumentType.PURCHASE_INVOICE, status);
            invoice.setReference("PINV-2026-00001");

            PurchaseDocumentResponse response = service.convertToCreditNote(5L);

            assertThat(response.getType()).isEqualTo(PurchaseDocumentType.PURCHASE_CREDIT_NOTE);
            assertThat(response.getStatus()).isEqualTo(PurchaseDocumentStatus.DRAFT);
            assertThat(response.getSourceReference()).isEqualTo("PINV-2026-00001");
            assertThat(response.getSourceType()).isEqualTo(PurchaseDocumentType.PURCHASE_INVOICE);
            assertThat(response.getWarehouseId()).isNull();
            assertThat(response.getTotal()).isEqualByComparingTo(invoice.getTotal());
            assertThat(response.getLines()).hasSize(1);
        }
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("what can be credited: a validated invoice that is not credited in full already")
    void creditNoteConversionRules() {
        existing(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a validated invoice");

        existing(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.CANCELLED);
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a validated invoice");

        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only an invoice can be credited");

        PurchaseDocument credited = existing(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.PAID);
        credited.setCreditedAmount(credited.getTotal());
        assertThatThrownBy(() -> service.convertToCreditNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("credited in full");
    }

    @Test
    @DisplayName("validating a credit note numbers it and takes its total off its invoice, without touching the stock")
    void validateCreditNote() {
        PurchaseDocument invoice = sourceInvoice(PurchaseDocumentStatus.VALIDATED, "100.000");
        PurchaseDocument note = creditNote(PurchaseDocumentStatus.DRAFT, invoice);
        when(numberingService.allocate(DocumentType.PURCHASE_CREDIT_NOTE)).thenReturn("PCN-2026-00001");
        creditedOnInvoice("35.700");

        PurchaseDocumentResponse response = service.validate(5L);

        assertThat(response.getReference()).isEqualTo("PCN-2026-00001");
        assertThat(note.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("35.700");
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PARTIALLY_PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("64.300");
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("a credit note that settles the invoice moves it to paid")
    void creditNoteSettlesTheInvoice() {
        PurchaseDocument invoice = sourceInvoice(PurchaseDocumentStatus.VALIDATED, "35.700");
        creditNote(PurchaseDocumentStatus.DRAFT, invoice);
        when(numberingService.allocate(DocumentType.PURCHASE_CREDIT_NOTE)).thenReturn("PCN-2026-00001");
        creditedOnInvoice("35.700");

        service.validate(5L);

        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PAID);
    }

    @Test
    @DisplayName("credit notes cannot take more off an invoice than it is worth")
    void creditNoteCannotExceedTheInvoice() {
        PurchaseDocument invoice = sourceInvoice(PurchaseDocumentStatus.PARTIALLY_PAID, "100.000");
        creditNote(PurchaseDocumentStatus.DRAFT, invoice);
        when(numberingService.allocate(DocumentType.PURCHASE_CREDIT_NOTE)).thenReturn("PCN-2026-00002");
        creditedOnInvoice("110.000"); // 74.3 credited before, this one 35.7 on top

        assertThatThrownBy(() -> service.validate(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This credit note is more than the 25.700 that can still be credited on PINV-2026-00001");
    }

    @Test
    @DisplayName("a credit note cannot be validated on an invoice that was cancelled meanwhile")
    void creditNoteOnCancelledInvoice() {
        creditNote(PurchaseDocumentStatus.DRAFT, sourceInvoice(PurchaseDocumentStatus.CANCELLED, "100.000"));
        when(numberingService.allocate(DocumentType.PURCHASE_CREDIT_NOTE)).thenReturn("PCN-2026-00001");

        assertThatThrownBy(() -> service.validate(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a validated invoice");
    }

    @Test
    @DisplayName("cancelling a credit note gives the invoice back what it took off")
    void cancelCreditNote() {
        PurchaseDocument invoice = sourceInvoice(PurchaseDocumentStatus.PARTIALLY_PAID, "100.000");
        invoice.setCreditedAmount(new BigDecimal("35.700"));
        PurchaseDocument note = creditNote(PurchaseDocumentStatus.VALIDATED, invoice);
        creditedOnInvoice("0");

        service.cancel(5L);

        assertThat(note.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);
        assertThat(invoice.getCreditedAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("an invoice with a live credit note cannot be cancelled until the credit note is")
    void invoiceWithCreditNoteCannotBeCancelled() {
        existing(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.PURCHASE_CREDIT_NOTE, PurchaseDocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has credit notes");
        verifyNoInteractions(stockService);
    }

    // ----- Supplier return notes -----

    private PurchaseDocument returnNote(PurchaseDocumentStatus status, PurchaseDocument source) {
        PurchaseDocument note = existing(PurchaseDocumentType.PURCHASE_RETURN_NOTE, status,
                good(20L, ProductKind.GOOD), good(21L, ProductKind.SERVICE));
        note.setWarehouse(warehouse);
        note.setSource(source);
        return note;
    }

    @Test
    @DisplayName("a return note needs a warehouse, and can be made by hand")
    void returnNoteNeedsWarehouse() {
        assertThatThrownBy(() -> service.create(
                request(PurchaseDocumentType.PURCHASE_RETURN_NOTE, freeLine("Item", "1", "10.000", null))))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A supplier return note needs a warehouse");

        PurchaseDocumentRequest request = request(PurchaseDocumentType.PURCHASE_RETURN_NOTE, freeLine("Item", "1", "10.000", null));
        request.setWarehouseId(1L);
        assertThat(service.create(request).getWarehouseName()).isEqualTo("Main");
    }

    @Test
    @DisplayName("validating a return note numbers it, then takes its goods - and only its goods - out of the warehouse")
    void validateReturnNoteTakesGoodsOut() {
        PurchaseDocument note = returnNote(PurchaseDocumentStatus.DRAFT, null);
        when(numberingService.allocate(DocumentType.PURCHASE_RETURN_NOTE)).thenReturn("PRN-2026-00001");

        PurchaseDocumentResponse response = service.validate(5L);

        assertThat(response.getReference()).isEqualTo("PRN-2026-00001");
        assertThat(note.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
        ArgumentCaptor<List<StockRequirement>> goods = ArgumentCaptor.forClass(List.class);
        verify(stockService).deliver(eq(StockSource.PURCHASE_DOCUMENT), eq(5L), eq(warehouse), goods.capture(), isNull());
        assertThat(goods.getValue()).extracting(g -> g.product().getId()).containsExactly(20L);
        verify(stockService, never()).receive(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a return note the stock refuses stays a draft")
    void validateReturnNoteRefusedByStock() {
        returnNote(PurchaseDocumentStatus.DRAFT, null);
        when(numberingService.allocate(DocumentType.PURCHASE_RETURN_NOTE)).thenReturn("PRN-2026-00001");
        doThrow(new BusinessRuleException("Not enough stock")).when(stockService).deliver(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.validate(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Not enough stock");
    }

    @Test
    @DisplayName("cancelling a validated return note brings its goods back into the stock")
    void cancelReturnNote() {
        PurchaseDocument note = returnNote(PurchaseDocumentStatus.VALIDATED, null);

        service.cancel(5L);

        assertThat(note.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);
        verify(stockService).undoDelivery(StockSource.PURCHASE_DOCUMENT, 5L, false);
        verify(stockService, never()).reverseReceipt(any(), any());
    }

    @Test
    @DisplayName("a goods receipt or an invoice with a live return note cannot be cancelled, and one with a dead return note can")
    void documentWithReturnNoteCannotBeCancelled() {
        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.PURCHASE_RETURN_NOTE, PurchaseDocumentStatus.VALIDATED)));
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has a return note");

        existing(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.cancel(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("has a return note");
        verify(stockService, never()).reverseReceipt(any(), any());

        when(documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(5L, COMPANY_ID)).thenReturn(List.of(
                other(PurchaseDocumentType.PURCHASE_RETURN_NOTE, PurchaseDocumentStatus.CANCELLED)));
        service.cancel(5L);
    }

    @Test
    @DisplayName("a validated goods receipt becomes a draft return note from the warehouse it went to, with the same lines")
    void convertsReceiptToReturnNote() {
        PurchaseDocument receipt = existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED);
        receipt.setReference("GR-2026-00001");

        PurchaseDocumentResponse response = service.convertToReturnNote(5L);

        assertThat(response.getType()).isEqualTo(PurchaseDocumentType.PURCHASE_RETURN_NOTE);
        assertThat(response.getStatus()).isEqualTo(PurchaseDocumentStatus.DRAFT);
        assertThat(response.getSourceReference()).isEqualTo("GR-2026-00001");
        assertThat(response.getSourceType()).isEqualTo(PurchaseDocumentType.GOODS_RECEIPT);
        assertThat(response.getWarehouseName()).isEqualTo("Main");
        assertThat(response.getLines()).hasSize(1);
        verifyNoInteractions(stockService); // a draft moves nothing
    }

    @Test
    @DisplayName("a validated invoice - paid or not - becomes a draft return note")
    void convertsInvoiceToReturnNote() {
        for (PurchaseDocumentStatus status : new PurchaseDocumentStatus[]{
                PurchaseDocumentStatus.VALIDATED, PurchaseDocumentStatus.PARTIALLY_PAID, PurchaseDocumentStatus.PAID}) {
            PurchaseDocument invoice = existing(PurchaseDocumentType.PURCHASE_INVOICE, status);
            invoice.setWarehouse(warehouse);

            PurchaseDocumentResponse response = service.convertToReturnNote(5L);

            assertThat(response.getType()).isEqualTo(PurchaseDocumentType.PURCHASE_RETURN_NOTE);
            assertThat(response.getSourceType()).isEqualTo(PurchaseDocumentType.PURCHASE_INVOICE);
        }
    }

    @Test
    @DisplayName("what can be returned: a validated receipt or invoice, not a draft, a cancelled document or an order")
    void returnNoteConversionRules() {
        existing(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.DRAFT);
        assertThatThrownBy(() -> service.convertToReturnNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a validated goods receipt");

        existing(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.CANCELLED);
        assertThatThrownBy(() -> service.convertToReturnNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a validated purchase invoice");

        existing(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        assertThatThrownBy(() -> service.convertToReturnNote(5L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("goods receipt or an invoice");
    }

    @Test
    @DisplayName("a return note made from a document keeps its supplier, one made by hand is free")
    void returnNoteKeepsItsSupplier() {
        returnNote(PurchaseDocumentStatus.DRAFT, other(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.VALIDATED));
        Supplier otherSupplier = new Supplier();
        otherSupplier.setId(4L);
        lenient().when(supplierService.getAssignable(4L)).thenReturn(otherSupplier);
        PurchaseDocumentRequest request = request(PurchaseDocumentType.PURCHASE_RETURN_NOTE, freeLine("Item", "1", "10.000", null));
        request.setSupplierId(4L);
        request.setWarehouseId(1L);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("supplier of the document");

        returnNote(PurchaseDocumentStatus.DRAFT, null);
        assertThat(service.update(5L, request).getSupplierId()).isEqualTo(4L);
    }

    // ----- Dashboard figures -----

    @Test
    @DisplayName("the purchase figures are net of supplier credit notes and cover the last six months, oldest first")
    void purchaseFigures() {
        when(documentRepository.sumTotal(eq(COMPANY_ID), eq(PurchaseDocumentType.PURCHASE_INVOICE), any(), any(), any()))
                .thenReturn(new BigDecimal("90.000"));
        when(documentRepository.sumTotal(eq(COMPANY_ID), eq(PurchaseDocumentType.PURCHASE_CREDIT_NOTE), any(), any(), any()))
                .thenReturn(new BigDecimal("30.000"));
        when(documentRepository.unpaid(eq(COMPANY_ID), eq(PurchaseDocumentType.PURCHASE_INVOICE), any()))
                .thenReturn(new com.sales.smartBusiness.common.AmountSummary(2L, new BigDecimal("110.000")));

        PurchaseFigures figures = service.figures(LocalDate.of(2026, 1, 15));

        assertThat(figures.today()).isEqualByComparingTo("60.000");
        assertThat(figures.month()).isEqualByComparingTo("60.000");
        assertThat(figures.months()).extracting(m -> m.month())
                .containsExactly("2025-08", "2025-09", "2025-10", "2025-11", "2025-12", "2026-01"); // across a year end
        assertThat(figures.unpaid().amount()).isEqualByComparingTo("110.000");
    }

    @Test
    @DisplayName("only validated, partly paid and paid invoices count as purchases, and only validated credit notes are taken off them")
    void purchaseFiguresCountTheRightStatuses() {
        when(documentRepository.sumTotal(any(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        service.figures(LocalDate.of(2026, 9, 20));

        verify(documentRepository, atLeastOnce()).sumTotal(eq(COMPANY_ID), eq(PurchaseDocumentType.PURCHASE_INVOICE),
                eq(List.of(PurchaseDocumentStatus.VALIDATED, PurchaseDocumentStatus.PARTIALLY_PAID, PurchaseDocumentStatus.PAID)),
                any(), any());
        verify(documentRepository, atLeastOnce()).sumTotal(eq(COMPANY_ID), eq(PurchaseDocumentType.PURCHASE_CREDIT_NOTE),
                eq(List.of(PurchaseDocumentStatus.VALIDATED)), any(), any());
    }
}
