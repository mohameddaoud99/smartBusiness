package com.sales.smartBusiness.common;

import java.math.BigDecimal;

/** One bar of a dashboard chart: a month ("2026-09") and its amount. */
public record MonthAmount(String month, BigDecimal amount) {
}
