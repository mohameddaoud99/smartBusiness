package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.tax.Tax;
import com.sales.smartBusiness.tax.TaxKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calculation chain and the workflow — pure objects, no Spring. The Finco example
 * (§07) is the reference: 30.000 HT, FODEC 1% → 0.300, VAT 19% on 30.300 → 5.757,
 * stamp 1.000 → 37.057.
 */
class SalesDocumentTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static Tax surcharge(String name, String rate, boolean inVatBase) {
        Tax tax = new Tax();
        tax.setId(1L);
        tax.setName(name);
        tax.setKind(TaxKind.PERCENTAGE_SURCHARGE);
        tax.setRate(bd(rate));
        tax.setIncludedInVatBase(inVatBase);
        return tax;
    }

    private static Tax stamp(String amount) {
        Tax tax = new Tax();
        tax.setId(2L);
        tax.setName("Stamp duty");
        tax.setKind(TaxKind.FIXED_PER_DOCUMENT);
        tax.setAmount(bd(amount));
        return tax;
    }

    private static SalesDocumentLine line(String quantity, String unitPrice, String discount, String vatRate) {
        SalesDocumentLine line = new SalesDocumentLine();
        line.setDesignation("Item");
        line.setQuantity(bd(quantity));
        line.setUnitPrice(bd(unitPrice));
        line.setDiscountRate(bd(discount));
        line.setVatRate(bd(vatRate));
        return line;
    }

    private static SalesDocument document(SalesDocumentType type, SalesDocumentLine... lines) {
        SalesDocument document = new SalesDocument();
        document.setType(type);
        for (SalesDocumentLine line : lines) {
            document.addLine(line);
        }
        return document;
    }

    private static BigDecimal amountOf(SalesDocument document, String name) {
        return document.getTaxes().stream()
                .filter(row -> row.getName().equals(name))
                .findFirst().orElseThrow()
                .getAmount();
    }

    // ----- Calculation -----

    @Test
    @DisplayName("reproduces the Finco reference case: FODEC in the VAT base, stamp after VAT")
    void fincoReferenceCase() {
        SalesDocument document = document(SalesDocumentType.QUOTE,
                line("1", "30.000", "0", "19"),
                line("5", "0.000", "0", "19"));
        document.selectTaxes(List.of(surcharge("FODEC", "1", true), stamp("1.000")));

        document.recalculate();

        assertThat(document.getSubtotal()).isEqualByComparingTo("30.000");
        assertThat(amountOf(document, "FODEC")).isEqualByComparingTo("0.300");
        assertThat(amountOf(document, "VAT 19%")).isEqualByComparingTo("5.757");
        assertThat(amountOf(document, "Stamp duty")).isEqualByComparingTo("1.000");
        assertThat(document.getTotal()).isEqualByComparingTo("37.057");
    }

    @Test
    @DisplayName("the VAT row shows the base it was computed on, surcharge included")
    void vatRowShowsItsBase() {
        SalesDocument document = document(SalesDocumentType.QUOTE, line("1", "30.000", "0", "19"));
        document.selectTaxes(List.of(surcharge("FODEC", "1", true)));

        document.recalculate();

        SalesDocumentTax vat = document.getTaxes().stream()
                .filter(row -> row.getKind() == TaxKind.VAT_RATE).findFirst().orElseThrow();
        assertThat(vat.getBase()).isEqualByComparingTo("30.300");
    }

    @Test
    @DisplayName("a surcharge outside the VAT base is added to the total but not taxed")
    void surchargeOutsideVatBase() {
        SalesDocument document = document(SalesDocumentType.QUOTE, line("1", "100.000", "0", "19"));
        document.selectTaxes(List.of(surcharge("Levy", "2", false)));

        document.recalculate();

        assertThat(amountOf(document, "Levy")).isEqualByComparingTo("2.000");
        assertThat(amountOf(document, "VAT 19%")).isEqualByComparingTo("19.000");
        assertThat(document.getTotal()).isEqualByComparingTo("121.000");
    }

    @Test
    @DisplayName("a line discount reduces the line before any tax")
    void lineDiscount() {
        SalesDocument document = document(SalesDocumentType.QUOTE, line("2", "50.000", "10", "19"));

        document.recalculate();

        assertThat(document.getLines().get(0).getLineTotal()).isEqualByComparingTo("90.000");
        assertThat(amountOf(document, "VAT 19%")).isEqualByComparingTo("17.100");
        assertThat(document.getTotal()).isEqualByComparingTo("107.100");
    }

    @Test
    @DisplayName("each VAT rate gets its own row, sorted by rate, on its own base")
    void severalVatRates() {
        SalesDocument document = document(SalesDocumentType.QUOTE,
                line("1", "100.000", "0", "19"),
                line("1", "200.000", "0", "7"),
                line("1", "50.000", "0", "19"));

        document.recalculate();

        List<SalesDocumentTax> rows = document.getTaxes();
        assertThat(rows).extracting(SalesDocumentTax::getName).containsExactly("VAT 7%", "VAT 19%");
        assertThat(rows.get(0).getBase()).isEqualByComparingTo("200.000");
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("14.000");
        assertThat(rows.get(1).getBase()).isEqualByComparingTo("150.000");
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("28.500");
        assertThat(document.getTotal()).isEqualByComparingTo("392.500");
    }

    @Test
    @DisplayName("the surcharge is spread over the VAT rates, so each base adds up")
    void surchargeSpreadOverRates() {
        SalesDocument document = document(SalesDocumentType.QUOTE,
                line("1", "100.000", "0", "19"),
                line("1", "100.000", "0", "7"));
        document.selectTaxes(List.of(surcharge("FODEC", "1", true)));

        document.recalculate();

        assertThat(amountOf(document, "FODEC")).isEqualByComparingTo("2.000");
        assertThat(amountOf(document, "VAT 7%")).isEqualByComparingTo("7.070");
        assertThat(amountOf(document, "VAT 19%")).isEqualByComparingTo("19.190");
        assertThat(document.getTotal()).isEqualByComparingTo("228.260");
    }

    @Test
    @DisplayName("amounts are rounded half-up to the millime")
    void roundsHalfUp() {
        // 3 x 0.0005 x 1 = 0.0015 -> 0.002 (half-up), not 0.001
        SalesDocument document = document(SalesDocumentType.QUOTE, line("3", "0.0005", "0", "0"));

        document.recalculate();

        assertThat(document.getLines().get(0).getLineTotal()).isEqualByComparingTo("0.002");
    }

    @Test
    @DisplayName("a line without VAT adds no tax but still gets a 0% recap row")
    void noVat() {
        SalesDocument document = document(SalesDocumentType.QUOTE, line("2", "10.000", "0", "0"));

        document.recalculate();

        assertThat(amountOf(document, "VAT 0%")).isEqualByComparingTo("0.000");
        assertThat(document.getTotal()).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("recomputing twice gives the same result and does not pile up rows")
    void recalculationIsIdempotent() {
        SalesDocument document = document(SalesDocumentType.QUOTE, line("1", "30.000", "0", "19"));
        document.selectTaxes(List.of(surcharge("FODEC", "1", true), stamp("1.000")));

        document.recalculate();
        document.recalculate();

        assertThat(document.getTaxes()).hasSize(3);
        assertThat(document.getTotal()).isEqualByComparingTo("37.057");
    }

    @Test
    @DisplayName("rows read in calculation order: surcharges, VAT, flat charges")
    void taxRowsAreOrdered() {
        SalesDocument document = document(SalesDocumentType.QUOTE, line("1", "30.000", "0", "19"));
        document.selectTaxes(List.of(stamp("1.000"), surcharge("FODEC", "1", true)));

        document.recalculate();

        assertThat(document.getTaxes()).extracting(SalesDocumentTax::getName)
                .containsExactly("FODEC", "VAT 19%", "Stamp duty");
        assertThat(document.getTaxes()).extracting(SalesDocumentTax::getPosition).containsExactly(0, 1, 2);
    }

    // ----- Workflow -----

    private static SalesDocument at(SalesDocumentType type, SalesDocumentStatus status) {
        SalesDocument document = new SalesDocument();
        document.setType(type);
        document.setStatus(status);
        return document;
    }

    @Test
    @DisplayName("a quote moves freely between issued, accepted and rejected")
    void quoteMovesFreely() {
        assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.ACCEPTED)).isTrue();
        assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.REJECTED)).isTrue();
        assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.ACCEPTED).canMoveTo(SalesDocumentStatus.REJECTED)).isTrue();
        assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.REJECTED).canMoveTo(SalesDocumentStatus.ISSUED)).isTrue();
    }

    @Test
    @DisplayName("a quote is never confirmed or cancelled")
    void quoteHasNoOrderStatuses() {
        assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CONFIRMED)).isFalse();
        assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("a sales order is confirmed or cancelled, and a cancelled one stays cancelled")
    void orderWorkflow() {
        assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CONFIRMED)).isTrue();
        assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CANCELLED)).isTrue();
        assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED).canMoveTo(SalesDocumentStatus.CANCELLED)).isTrue();
        assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED).canMoveTo(SalesDocumentStatus.ISSUED)).isFalse();
        assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CANCELLED).canMoveTo(SalesDocumentStatus.CONFIRMED)).isFalse();
        assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.ACCEPTED)).isFalse();
    }

    @Test
    @DisplayName("leaving a draft is not a status change — it has its own operation")
    void draftHasNoStatusChange() {
        for (SalesDocumentStatus target : SalesDocumentStatus.values()) {
            assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.DRAFT).canMoveTo(target)).isFalse();
            assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.DRAFT).canMoveTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("copying a quote into an order keeps lines, price and taxes and recomputes the same total")
    void copyContent() {
        SalesDocument quote = document(SalesDocumentType.QUOTE, line("1", "30.000", "0", "19"));
        quote.setNotes("Thanks");
        quote.selectTaxes(List.of(surcharge("FODEC", "1", true), stamp("1.000")));
        quote.recalculate();

        SalesDocument order = new SalesDocument();
        order.setType(SalesDocumentType.SALES_ORDER);
        order.copyContentFrom(quote);

        assertThat(order.getLines()).hasSize(1);
        assertThat(order.getLines().get(0)).isNotSameAs(quote.getLines().get(0));
        assertThat(order.getNotes()).isEqualTo("Thanks");
        assertThat(order.getTaxes()).hasSize(3);
        assertThat(order.getTotal()).isEqualByComparingTo(quote.getTotal());
    }

    @Test
    @DisplayName("a delivery note is created, then delivered, and can be cancelled before or after")
    void deliveryNoteWorkflow() {
        assertThat(at(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.DELIVERED)).isTrue();
        assertThat(at(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CANCELLED)).isTrue();
        assertThat(at(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED).canMoveTo(SalesDocumentStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("a delivery note is never confirmed, accepted or rejected, and cannot go back once delivered")
    void deliveryNoteHasNoOtherStatuses() {
        for (SalesDocumentStatus target : new SalesDocumentStatus[]{
                SalesDocumentStatus.CONFIRMED, SalesDocumentStatus.ACCEPTED, SalesDocumentStatus.REJECTED, SalesDocumentStatus.ISSUED}) {
            assertThat(at(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.ISSUED).canMoveTo(target)).isFalse();
        }
        assertThat(at(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DELIVERED).canMoveTo(SalesDocumentStatus.ISSUED)).isFalse();
        assertThat(at(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.CANCELLED).canMoveTo(SalesDocumentStatus.DELIVERED)).isFalse();
        assertThat(at(SalesDocumentType.DELIVERY_NOTE, SalesDocumentStatus.DRAFT).canMoveTo(SalesDocumentStatus.DELIVERED)).isFalse();
    }

    @Test
    @DisplayName("an order is never delivered, and a quote is never delivered either")
    void onlyDeliveryNotesAreDelivered() {
        assertThat(at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED).canMoveTo(SalesDocumentStatus.DELIVERED)).isFalse();
        assertThat(at(SalesDocumentType.QUOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.DELIVERED)).isFalse();
    }

    // ----- Invoices -----

    @Test
    @DisplayName("an invoice nobody has paid can be cancelled; once paid or part paid, only its payments move it")
    void invoiceWorkflow() {
        assertThat(at(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CANCELLED)).isTrue();
        assertThat(at(SalesDocumentType.INVOICE, SalesDocumentStatus.PARTIALLY_PAID).canMoveTo(SalesDocumentStatus.CANCELLED)).isFalse();
        assertThat(at(SalesDocumentType.INVOICE, SalesDocumentStatus.PAID).canMoveTo(SalesDocumentStatus.CANCELLED)).isFalse();
        for (SalesDocumentStatus target : new SalesDocumentStatus[]{
                SalesDocumentStatus.PAID, SalesDocumentStatus.PARTIALLY_PAID, SalesDocumentStatus.CONFIRMED, SalesDocumentStatus.DELIVERED}) {
            assertThat(at(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED).canMoveTo(target)).isFalse();
        }
        assertThat(at(SalesDocumentType.INVOICE, SalesDocumentStatus.CANCELLED).canMoveTo(SalesDocumentStatus.ISSUED)).isFalse();
    }

    @Test
    @DisplayName("the paid amount decides the status: nothing paid, part paid, paid in full")
    void applyPaidMovesTheStatus() {
        SalesDocument invoice = at(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED);
        invoice.setTotal(bd("37.057"));

        invoice.applyPaid(bd("10.000"));
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PARTIALLY_PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("27.057");

        invoice.applyPaid(bd("37.057"));
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("0");

        invoice.applyPaid(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.ISSUED);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("only a delivery note and an invoice name a warehouse")
    void whichTypesHaveAWarehouse() {
        assertThat(SalesDocumentType.INVOICE.hasWarehouse()).isTrue();
        assertThat(SalesDocumentType.DELIVERY_NOTE.hasWarehouse()).isTrue();
        assertThat(SalesDocumentType.QUOTE.hasWarehouse()).isFalse();
        assertThat(SalesDocumentType.SALES_ORDER.hasWarehouse()).isFalse();
    }

    // ----- Credit notes -----

    @Test
    @DisplayName("a credit note is issued at once against its invoice, and can only be cancelled")
    void creditNoteWorkflow() {
        assertThat(at(SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CANCELLED)).isTrue();
        for (SalesDocumentStatus target : new SalesDocumentStatus[]{
                SalesDocumentStatus.CONFIRMED, SalesDocumentStatus.DELIVERED, SalesDocumentStatus.PAID, SalesDocumentStatus.ACCEPTED}) {
            assertThat(at(SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.ISSUED).canMoveTo(target)).isFalse();
        }
        assertThat(at(SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.CANCELLED).canMoveTo(SalesDocumentStatus.ISSUED)).isFalse();
        assertThat(at(SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.DRAFT).canMoveTo(SalesDocumentStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("credit notes and payments settle an invoice together")
    void creditAndPaymentSettleTogether() {
        SalesDocument invoice = at(SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED);
        invoice.setTotal(bd("100.000"));

        invoice.applyCredited(bd("30.000"));
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PARTIALLY_PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("70");

        invoice.applyPaid(bd("70.000"));
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("0");

        invoice.applyCredited(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PARTIALLY_PAID);
        invoice.applyPaid(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.ISSUED);
    }

    @Test
    @DisplayName("credited after being paid in full, an invoice is owed nothing and the customer is owed the difference")
    void creditOfAPaidInvoiceLeavesANegativeBalance() {
        SalesDocument invoice = at(SalesDocumentType.INVOICE, SalesDocumentStatus.PAID);
        invoice.setTotal(bd("100.000"));
        invoice.setPaidAmount(bd("100.000"));

        invoice.applyCredited(bd("30.000"));

        assertThat(invoice.getStatus()).isEqualTo(SalesDocumentStatus.PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("-30");
    }

    @Test
    @DisplayName("a cancelled invoice, a draft, or any other document does not follow payments or credit")
    void onlyLiveInvoicesFollowTheirSettlement() {
        SalesDocument cancelled = at(SalesDocumentType.INVOICE, SalesDocumentStatus.CANCELLED);
        cancelled.applyPaid(bd("5"));
        assertThat(cancelled.getStatus()).isEqualTo(SalesDocumentStatus.CANCELLED);

        SalesDocument draft = at(SalesDocumentType.INVOICE, SalesDocumentStatus.DRAFT);
        draft.applyCredited(bd("5"));
        assertThat(draft.getStatus()).isEqualTo(SalesDocumentStatus.DRAFT);

        SalesDocument order = at(SalesDocumentType.SALES_ORDER, SalesDocumentStatus.CONFIRMED);
        order.applyPaid(bd("5"));
        assertThat(order.getStatus()).isEqualTo(SalesDocumentStatus.CONFIRMED);
    }

    // ----- Return notes -----

    @Test
    @DisplayName("a return note is issued at once - its goods are back - and can only be cancelled")
    void returnNoteWorkflow() {
        assertThat(at(SalesDocumentType.RETURN_NOTE, SalesDocumentStatus.ISSUED).canMoveTo(SalesDocumentStatus.CANCELLED)).isTrue();
        for (SalesDocumentStatus target : new SalesDocumentStatus[]{
                SalesDocumentStatus.CONFIRMED, SalesDocumentStatus.DELIVERED, SalesDocumentStatus.PAID, SalesDocumentStatus.ACCEPTED}) {
            assertThat(at(SalesDocumentType.RETURN_NOTE, SalesDocumentStatus.ISSUED).canMoveTo(target)).isFalse();
        }
        assertThat(at(SalesDocumentType.RETURN_NOTE, SalesDocumentStatus.CANCELLED).canMoveTo(SalesDocumentStatus.ISSUED)).isFalse();
        assertThat(at(SalesDocumentType.RETURN_NOTE, SalesDocumentStatus.DRAFT).canMoveTo(SalesDocumentStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("a return note names the warehouse its goods come back to")
    void returnNoteHasAWarehouse() {
        assertThat(SalesDocumentType.RETURN_NOTE.hasWarehouse()).isTrue();
    }

    @Test
    @DisplayName("a copy remembers the line it came from, takes only the quantity it is given, and skips a line with nothing left")
    void copyFollowsSourceLines() {
        SalesDocument order = document(SalesDocumentType.SALES_ORDER, line("10", "5.000", "0", "0"), line("4", "2.000", "0", "0"));

        SalesDocument note = new SalesDocument();
        note.setType(SalesDocumentType.DELIVERY_NOTE);
        note.copyContentFrom(order, original -> original.getQuantity().compareTo(bd("4")) == 0 ? BigDecimal.ZERO : bd("6"));

        assertThat(note.getLines()).hasSize(1);
        assertThat(note.getLines().get(0).getSourceLine()).isSameAs(order.getLines().get(0));
        assertThat(note.getLines().get(0).getQuantity()).isEqualByComparingTo("6");
        assertThat(note.getTotal()).isEqualByComparingTo("30.000");
    }

    @Test
    @DisplayName("a plain copy takes every quantity in full and still remembers the source lines")
    void plainCopyKeepsSourceLines() {
        SalesDocument order = document(SalesDocumentType.SALES_ORDER, line("3", "5.000", "0", "0"));

        SalesDocument note = new SalesDocument();
        note.copyContentFrom(order);

        assertThat(note.getLines().get(0).getQuantity()).isEqualByComparingTo("3");
        assertThat(note.getLines().get(0).getSourceLine()).isSameAs(order.getLines().get(0));
    }
}
