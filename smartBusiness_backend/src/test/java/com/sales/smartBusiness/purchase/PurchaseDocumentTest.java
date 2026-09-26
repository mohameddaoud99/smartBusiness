package com.sales.smartBusiness.purchase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseDocumentTest {

    private static PurchaseDocument at(PurchaseDocumentType type, PurchaseDocumentStatus status) {
        PurchaseDocument document = new PurchaseDocument();
        document.setType(type);
        document.setStatus(status);
        return document;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("an invoice nobody has paid can be cancelled; once paid or part paid, only its payments move it")
    void invoiceWorkflow() {
        assertThat(at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isTrue();
        assertThat(at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.PARTIALLY_PAID)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isFalse();
        assertThat(at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.PAID)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isFalse();
        assertThat(at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.DRAFT)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isFalse();
    }

    @Test
    @DisplayName("the paid amount decides the status: nothing paid, part paid, paid in full")
    void applyPaidMovesTheStatus() {
        PurchaseDocument invoice = at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED);
        invoice.setTotal(bd("37.057"));

        invoice.applyPaid(bd("10.000"));
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PARTIALLY_PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("27.057");

        invoice.applyPaid(bd("37.057"));
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("0");

        invoice.applyPaid(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a cancelled invoice, a draft, or any other document does not follow payments")
    void onlyLiveInvoicesFollowTheirPayments() {
        PurchaseDocument cancelled = at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.CANCELLED);
        cancelled.applyPaid(bd("5"));
        assertThat(cancelled.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);

        PurchaseDocument draft = at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.DRAFT);
        draft.applyPaid(bd("5"));
        assertThat(draft.getStatus()).isEqualTo(PurchaseDocumentStatus.DRAFT);

        PurchaseDocument order = at(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        order.applyPaid(bd("5"));
        assertThat(order.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
    }

    @Test
    @DisplayName("only a goods receipt and an invoice name a warehouse")
    void whichTypesHaveAWarehouse() {
        assertThat(PurchaseDocumentType.GOODS_RECEIPT.hasWarehouse()).isTrue();
        assertThat(PurchaseDocumentType.PURCHASE_INVOICE.hasWarehouse()).isTrue();
        assertThat(PurchaseDocumentType.PURCHASE_ORDER.hasWarehouse()).isFalse();
    }

    // ----- Supplier credit notes -----

    @Test
    @DisplayName("a supplier credit note is validated at once against its invoice, and can only be cancelled")
    void creditNoteWorkflow() {
        assertThat(at(PurchaseDocumentType.PURCHASE_CREDIT_NOTE, PurchaseDocumentStatus.VALIDATED)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isTrue();
        assertThat(at(PurchaseDocumentType.PURCHASE_CREDIT_NOTE, PurchaseDocumentStatus.DRAFT)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isFalse();
        assertThat(at(PurchaseDocumentType.PURCHASE_CREDIT_NOTE, PurchaseDocumentStatus.CANCELLED)
                .canMoveTo(PurchaseDocumentStatus.VALIDATED)).isFalse();
        assertThat(PurchaseDocumentType.PURCHASE_CREDIT_NOTE.hasWarehouse()).isFalse();
    }

    @Test
    @DisplayName("credit notes and payments settle an invoice together")
    void creditAndPaymentSettleTogether() {
        PurchaseDocument invoice = at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.VALIDATED);
        invoice.setTotal(bd("100.000"));

        invoice.applyCredited(bd("30.000"));
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PARTIALLY_PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("70");

        invoice.applyPaid(bd("70.000"));
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("0");

        invoice.applyCredited(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PARTIALLY_PAID);
        invoice.applyPaid(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
    }

    @Test
    @DisplayName("credited after being paid in full, an invoice leaves the supplier owing us the difference: a negative balance")
    void creditOfAPaidInvoiceLeavesANegativeBalance() {
        PurchaseDocument invoice = at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.PAID);
        invoice.setTotal(bd("100.000"));
        invoice.setPaidAmount(bd("100.000"));

        invoice.applyCredited(bd("30.000"));

        assertThat(invoice.getStatus()).isEqualTo(PurchaseDocumentStatus.PAID);
        assertThat(invoice.getBalance()).isEqualByComparingTo("-30");
    }

    @Test
    @DisplayName("a cancelled invoice, a draft, or any other document does not follow credit")
    void onlyLiveInvoicesFollowTheirSettlement() {
        PurchaseDocument cancelled = at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.CANCELLED);
        cancelled.applyCredited(bd("5"));
        assertThat(cancelled.getStatus()).isEqualTo(PurchaseDocumentStatus.CANCELLED);

        PurchaseDocument draft = at(PurchaseDocumentType.PURCHASE_INVOICE, PurchaseDocumentStatus.DRAFT);
        draft.applyCredited(bd("5"));
        assertThat(draft.getStatus()).isEqualTo(PurchaseDocumentStatus.DRAFT);

        PurchaseDocument order = at(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        order.applyCredited(bd("5"));
        assertThat(order.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
    }

    // ----- Supplier return notes -----

    @Test
    @DisplayName("a supplier return note is validated at once - its goods are gone - and can only be cancelled")
    void returnNoteWorkflow() {
        assertThat(at(PurchaseDocumentType.PURCHASE_RETURN_NOTE, PurchaseDocumentStatus.VALIDATED)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isTrue();
        assertThat(at(PurchaseDocumentType.PURCHASE_RETURN_NOTE, PurchaseDocumentStatus.DRAFT)
                .canMoveTo(PurchaseDocumentStatus.CANCELLED)).isFalse();
        assertThat(PurchaseDocumentType.PURCHASE_RETURN_NOTE.hasWarehouse()).isTrue();
    }

    @Test
    @DisplayName("a return note is not settled by payments: it does not follow them")
    void returnNoteIgnoresPayments() {
        PurchaseDocument note = at(PurchaseDocumentType.PURCHASE_RETURN_NOTE, PurchaseDocumentStatus.VALIDATED);
        note.applyPaid(bd("5"));
        assertThat(note.getStatus()).isEqualTo(PurchaseDocumentStatus.VALIDATED);
    }

    private static PurchaseDocumentLine line(String quantity) {
        PurchaseDocumentLine line = new PurchaseDocumentLine();
        line.setDesignation("Item");
        line.setQuantity(bd(quantity));
        line.setUnitPrice(bd("5.000"));
        line.setDiscountRate(BigDecimal.ZERO);
        line.setVatRate(BigDecimal.ZERO);
        return line;
    }

    @Test
    @DisplayName("a copy remembers the line it came from, takes only the quantity it is given, and skips a line with nothing left")
    void copyFollowsSourceLines() {
        PurchaseDocument order = at(PurchaseDocumentType.PURCHASE_ORDER, PurchaseDocumentStatus.VALIDATED);
        order.addLine(line("10"));
        order.addLine(line("4"));

        PurchaseDocument receipt = at(PurchaseDocumentType.GOODS_RECEIPT, PurchaseDocumentStatus.DRAFT);
        receipt.copyContentFrom(order, original -> original.getQuantity().compareTo(bd("4")) == 0 ? BigDecimal.ZERO : bd("6"));

        assertThat(receipt.getLines()).hasSize(1);
        assertThat(receipt.getLines().get(0).getSourceLine()).isSameAs(order.getLines().get(0));
        assertThat(receipt.getLines().get(0).getQuantity()).isEqualByComparingTo("6");
        assertThat(receipt.getTotal()).isEqualByComparingTo("30.000");
    }
}
