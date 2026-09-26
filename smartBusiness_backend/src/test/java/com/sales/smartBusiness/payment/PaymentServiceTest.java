package com.sales.smartBusiness.payment;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.customer.Customer;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.sales.SalesDocument;
import com.sales.smartBusiness.sales.SalesDocumentService;
import com.sales.smartBusiness.sales.SalesDocumentType;
import com.sales.smartBusiness.security.CurrentUser;
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
class PaymentServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private PaymentRepository paymentRepository;
    @Mock private SalesDocumentService salesDocumentService;
    @Mock private CompanyService companyService;
    @Mock private CurrentUser currentUser;
    @Spy private PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

    @InjectMocks private PaymentService service;

    private Company company;
    private SalesDocument invoice;

    @BeforeEach
    void setUp() {
        company = new Company();
        Customer customer = new Customer();
        customer.setName("Client Alpha");
        invoice = new SalesDocument();
        invoice.setId(5L);
        invoice.setType(SalesDocumentType.INVOICE);
        invoice.setCustomer(customer);
        invoice.setReference("INV-2026-00001");
        invoice.setTotal(new BigDecimal("50.000"));

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(companyService.currentReference()).thenReturn(company);
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> {
            Payment saved = call.getArgument(0);
            saved.setId(30L);
            return saved;
        });
    }

    private static PaymentRequest request(String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(5L);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentDate(LocalDate.of(2026, 9, 21));
        request.setMethod(PaymentMethod.CHECK);
        request.setReference("  CHQ-1  ");
        return request;
    }

    private void alreadyPaid(String amount) {
        when(salesDocumentService.getPayableInvoice(5L)).thenReturn(invoice);
        when(paymentRepository.sumByInvoice(COMPANY_ID, 5L, PaymentStatus.ACTIVE)).thenReturn(new BigDecimal(amount));
    }

    @Test
    @DisplayName("a payment is recorded on the invoice, then the invoice is told the SUM of its payments")
    void createRecordsAndTellsTheInvoice() {
        alreadyPaid("20.000");

        PaymentResponse response = service.create(request("10.000"));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.ACTIVE);
        assertThat(response.getInvoiceReference()).isEqualTo("INV-2026-00001");
        assertThat(response.getCustomerName()).isEqualTo("Client Alpha");
        assertThat(response.getReference()).isEqualTo("CHQ-1");
        var saved = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getCompany()).isSameAs(company);
        assertThat(saved.getValue().getInvoice()).isSameAs(invoice);
        verify(salesDocumentService).applyPaid(5L, new BigDecimal("30.000"));
    }

    @Test
    @DisplayName("a payment cannot exceed what is still due, but can settle it exactly")
    void noOverpayment() {
        alreadyPaid("40.000");

        assertThatThrownBy(() -> service.create(request("10.001")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This payment is more than the 10.000 still due on the invoice");
        verify(paymentRepository, never()).save(any());
        verify(salesDocumentService, never()).applyPaid(any(), any());

        service.create(request("10.000"));
        verify(salesDocumentService).applyPaid(5L, new BigDecimal("50.000"));
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
        verify(salesDocumentService).applyPaid(5L, new BigDecimal("20.000"));
    }

    @Test
    @DisplayName("an invoice that cannot be paid stops the payment before anything is written")
    void unpayableInvoice() {
        when(salesDocumentService.getPayableInvoice(5L)).thenThrow(new BusinessRuleException("This invoice is cancelled"));

        assertThatThrownBy(() -> service.create(request("1")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("This invoice is cancelled");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("a blank reference and blank notes are stored as nothing")
    void blankTextIsNull() {
        alreadyPaid("0");
        PaymentRequest request = request("1");
        request.setReference("   ");
        request.setNotes("");

        PaymentResponse response = service.create(request);

        assertThat(response.getReference()).isNull();
        assertThat(response.getNotes()).isNull();
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
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
        Payment payment = payment(PaymentStatus.ACTIVE);
        when(paymentRepository.sumByInvoice(COMPANY_ID, 5L, PaymentStatus.ACTIVE)).thenReturn(new BigDecimal("20.000"));

        PaymentResponse response = service.cancel(30L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentRepository).flush(); // so the sum below no longer counts it
        verify(salesDocumentService).applyPaid(5L, new BigDecimal("20.000"));
    }

    @Test
    @DisplayName("a payment is cancelled once")
    void cancelTwiceRefused() {
        payment(PaymentStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancel(30L))
                .isInstanceOf(BusinessRuleException.class).hasMessage("This payment is already cancelled");
        verify(salesDocumentService, never()).applyPaid(any(), any());
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
