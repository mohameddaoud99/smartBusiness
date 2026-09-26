package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.common.AmountSummary;
import com.sales.smartBusiness.common.MonthAmount;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the dashboard says of the purchases: net purchases (invoices minus supplier credit notes) today and this
 * month, what is still to pay suppliers, what is late, the late invoices themselves, and the last six months.
 */
public record PurchaseFigures(BigDecimal today,
                              BigDecimal month,
                              AmountSummary unpaid,
                              AmountSummary overdue,
                              List<PurchaseDocumentSummaryResponse> overdueInvoices,
                              List<MonthAmount> months) {
}
