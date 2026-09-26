package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.common.AmountSummary;
import com.sales.smartBusiness.common.MonthAmount;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the dashboard says of the sales: net revenue (invoices minus credit notes) today and this month, what
 * customers still owe, what is late, the late invoices themselves, and the last six months.
 */
public record SalesFigures(BigDecimal today,
                           BigDecimal month,
                           AmountSummary unpaid,
                           AmountSummary overdue,
                           List<SalesDocumentSummaryResponse> overdueInvoices,
                           List<MonthAmount> months) {
}
