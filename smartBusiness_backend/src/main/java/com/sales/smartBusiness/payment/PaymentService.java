package com.sales.smartBusiness.payment;

import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.sales.SalesDocument;
import com.sales.smartBusiness.sales.SalesDocumentService;
import com.sales.smartBusiness.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SalesDocumentService salesDocumentService;
    private final CompanyService companyService;
    private final PaymentMapper paymentMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> search(Long invoiceId, Pageable pageable) {
        return paymentRepository.search(currentUser.companyId(), invoiceId, pageable)
                .map(paymentMapper::toResponse);
    }

    /**
     * Records money received on an issued invoice. It cannot exceed what is still due, so an invoice is
     * never over-paid; then the invoice is told the new SUM of its active payments and moves its status.
     */
    public PaymentResponse create(PaymentRequest request) {
        SalesDocument invoice = salesDocumentService.getPayableInvoice(request.getInvoiceId());
        BigDecimal alreadyPaid = paidOn(invoice.getId());
        BigDecimal due = invoice.getTotal().subtract(invoice.getCreditedAmount()).subtract(alreadyPaid);
        if (request.getAmount().compareTo(due) > 0) {
            throw new BusinessRuleException("This payment is more than the " + due.toPlainString()
                    + " still due on the invoice");
        }

        Payment payment = paymentMapper.toEntity(request);
        payment.setCompany(companyService.currentReference());
        payment.setInvoice(invoice);
        payment.setReference(trimToNull(request.getReference()));
        payment.setNotes(trimToNull(request.getNotes()));
        payment.setStatus(PaymentStatus.ACTIVE);
        paymentRepository.save(payment);

        salesDocumentService.applyPaid(invoice.getId(), alreadyPaid.add(request.getAmount()));
        return paymentMapper.toResponse(payment);
    }

    /** A wrongly recorded payment is cancelled, not deleted: the invoice gets back what it was owed. */
    public PaymentResponse cancel(Long id) {
        Payment payment = paymentRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", id));
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new BusinessRuleException("This payment is already cancelled");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.flush();
        Long invoiceId = payment.getInvoice().getId();
        salesDocumentService.applyPaid(invoiceId, paidOn(invoiceId));
        return paymentMapper.toResponse(payment);
    }

    private BigDecimal paidOn(Long invoiceId) {
        return paymentRepository.sumByInvoice(currentUser.companyId(), invoiceId, PaymentStatus.ACTIVE);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
