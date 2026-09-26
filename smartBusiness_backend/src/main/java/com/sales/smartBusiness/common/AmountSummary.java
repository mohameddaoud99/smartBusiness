package com.sales.smartBusiness.common;

import java.math.BigDecimal;

/** How many documents, and how much they add up to - what a dashboard card says of a group of invoices. */
public record AmountSummary(Long count, BigDecimal amount) {
}
