import { SalesDocumentSummary } from '../sales/sales-document.model';
import { PurchaseDocumentSummary } from '../purchases/purchase-document.model';
import { StockLevel } from '../stock/stock.model';

/** How many documents, and how much they add up to. */
export interface AmountSummary {
  count: number;
  amount: number;
}

/** One month of a chart: "2026-09" and its amount. */
export interface MonthAmount {
  month: string;
  amount: number;
}

/** Net revenue (invoices minus credit notes), what customers still owe, what is late — all computed by the server. */
export interface SalesFigures {
  today: number;
  month: number;
  unpaid: AmountSummary;
  overdue: AmountSummary;
  overdueInvoices: SalesDocumentSummary[];
  /** The last six months, oldest first. */
  months: MonthAmount[];
}

export interface PurchaseFigures {
  today: number;
  month: number;
  unpaid: AmountSummary;
  overdue: AmountSummary;
  overdueInvoices: PurchaseDocumentSummary[];
  months: MonthAmount[];
}

/** What the stock is worth at purchase price, and the goods at or under their minimum. */
export interface StockFigures {
  value: number;
  lowStockCount: number;
  lowStock: StockLevel[];
}
