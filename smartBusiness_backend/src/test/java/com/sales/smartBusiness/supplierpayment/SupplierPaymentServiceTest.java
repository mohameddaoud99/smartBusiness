package com.sales.smartBusiness.supplierpayment;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.payment.PaymentMethod;
import com.sales.smartBusiness.payment.PaymentRequest;
import com.sales.smartBusiness.payment.PaymentStatus;
import com.sales.smartBusiness.purchase.PurchaseDocument;
import com.sales.smartBusiness.purchase.PurchaseDocumentService;
import com.sales.smartBusiness.purchase.PurchaseDocumentType;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.supplier.Supplier;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierPaymentServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private SupplierPaymentRepository paymentRepository;
    @Mock private PurchaseDocumentService purchaseDocumentService;
    @Mock private CompanyService companyService;
    @Mock private CurrentUser currentUser;
    @Spy private SupplierPaymentMapper paymentMapper = Mappers.getMapper(SupplierPaymentMapper.class);

    @InjectMocks private SupplierPaymentService service;

    private Company company;
    private PurchaseDocument invoice;

    @BeforeEach
    void setUp() {
        company = new Company();
        Supplier supplier = new Supplier();
        supplier.setName("Supplier Alpha");
        invoice = new PurchaseDocument();
        invoice.setId(5L);
        invoice.setType(PurchaseDocumentType.PURCHASE_INVOICE);
        invoice.setSupplier(supplier);
        invoice.setReference("PINV-2026-00001");
        invoice.setTotal(new BigDecimal("50.000"));

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(companyService.currentReference()).thenReturn(company);
        lenient().when(paymentRepository.save(any(SupplierPayment.class))).thenAnswer(call -> {
            SupplierPayment saved = call.getArgument(0);
            saved.setId(30L);
            return saved;
        });
    }

    private static PaymentRequest request(String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(5L);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentDate(LocalDate.of(2026, 9, 21));
        request.setMethod(PaymentMethod.BANK_TRANSFER);
        request.setReference("  VIR-1  ");
        return request;
    }

    private void alreadyPaid(String amount) {
        when(purchaseDocumentService.getPayableInvoice(5L)).thenReturn(invoice);
        when(paymentRepository.sumByInvoice(COMPANY_ID, 5L, PaymentStatus.ACTIVE)).thenReturn(new BigDecimal(amount));
    }

    @Test
    @DisplayName("a payment is recorded on the invoice, then the invoice is told the SUM of its payments")
    void createRecordsAndTellsTheInvoice() {
        alreadyPaid("20.000");

        SupplierPaymentResponse response = service.create(request("10.000"));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.ACTIVE);
        assertThat(response.getInvoiceReference()).isEqualTo("PINV-2026-00001");
        assertThat(response.getSupplierName()).isEqualTo("Supplier Alpha");
        assertThat(response.getReference()).isEqualTo("VIR-1");
        var saved = org.mockito.ArgumentCaptor.forClass(SupplierPayment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getCompany()).isSameAs(company);
        assertThat(saved.getValue().getInvoice()).isSameAs(invoice);
        verify(purchaseDocumentService).applyPaid(5L, new BigDecimal("30.000"));
    }

    @Test
    @DisplayName("a payment cannot exceed what is still due, but can settle it exactly")
    void noOverpayment() {
        alreadyPaid("40.000");

        assertThatThrownBy(() -> service.create(request("10.001")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This payment is more than the 10.000 still due on the invoice");
        verify(paymentRepository, never()).save(any());
        verify(purchaseDocumentService, never()).applyPaid(any(), any());

        service.create(request("10.000"));
        verify(purchaseDocumentService).applyPaid(5L, new BigDecimal("50.000"));
    }

    @Test
    @DisplayName("what a credit note took off the invoice is no longer due")
    void creditedAmountIsNotDue() {
        invoice.setCreditedAmount(new BigDecimal("30.000"));
        alreadyPaid("10.000"); // 50 - 30 credited - 10 paid = 10 due

        assertThatThrownBy(() -> service.create(request("10.001")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This payment is more than the 10.000 still due on the invoice");

        service.create(request("10.000"));
        verify(purchaseDocumentService).applyPaid(5L, new BigDecimal("20.000"));
    }

    @Test
    @DisplayName("an invoice that cannot be paid stops the payment before anything is written")
    void unpayableInvoice() {
        when(purchaseDocumentService.getPayableInvoice(5L)).thenThrow(new BusinessRuleException("This invoice is cancelled"));

        assertThatThrownBy(() -> service.create(request("1")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("This invoice is cancelled");
        verify(paymentRepository, never()).save(any());
    }

    private SupplierPayment payment(PaymentStatus status) {
        SupplierPayment payment = new SupplierPayment();
        payment.setId(30L);
        payment.setInvoice(invoice);
        payment.setAmount(new BigDecimal("10.000"));
        payment.setStatus(status);
        when(paymentRepository.findByIdAndCompanyId(30L, COMPANY_ID)).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test
    @DisplayName("cancelling a payment gives the invoice back the sum of the payments that are left")
    void cancelRewritesThePaidAmount() {
        SupplierPayment payment = payment(PaymentStatus.ACTIVE);
        when(paymentRepository.sumByInvoice(COMPANY_ID, 5L, PaymentStatus.ACTIVE)).thenReturn(new BigDecimal("20.000"));

        SupplierPaymentResponse response = service.cancel(30L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentRepository).flush();
        verify(purchaseDocumentService).applyPaid(5L, new BigDecimal("20.000"));
    }

    @Test
    @DisplayName("a payment is cancelled once")
    void cancelTwiceRefused() {
        payment(PaymentStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancel(30L))
                .isInstanceOf(BusinessRuleException.class).hasMessage("This payment is already cancelled");
        verify(purchaseDocumentService, never()).applyPaid(any(), any());
    }

    @Test
    @DisplayName("an unknown or foreign payment is a 404")
    void unknownPayment() {
        when(paymentRepository.findByIdAndCompanyId(99L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("search is always scoped to the caller's company")
    void searchIsScoped() {
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentRepository.search(COMPANY_ID, 5L, pageable)).thenReturn(org.springframework.data.domain.Page.empty());

        service.search(5L, pageable);

        verify(paymentRepository).search(COMPANY_ID, 5L, pageable);
        verify(paymentRepository, never()).findAll();
    }
}
