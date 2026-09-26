import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TableModule } from 'primeng/table';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { BarChartComponent, BarPoint } from '../../shared/components/bar-chart/bar-chart.component';
import { AuthService } from '../../core/auth/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { MonthAmount, PurchaseFigures, SalesFigures, StockFigures } from './dashboard.model';

/** One card of a row: what it counts, its value, a short note, and whether it deserves attention. */
export interface Kpi {
  label: string;
  icon: string;
  value: number;
  /** An amount is shown with its millimes, a count as a whole number. */
  format: 'amount' | 'count';
  note: string;
  tone?: 'danger' | 'warn';
}

/**
 * The home page. Each area — sales, purchases, stock — is asked for separately and only when the person holds the
 * right to that module, so what they see is what they may see there (the server refuses the rest anyway). Every
 * figure is computed by the server; nothing is added up here.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DatePipe, DecimalPipe, NgTemplateOutlet, RouterLink, TableModule, PageHeaderComponent, BarChartComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {

  private readonly auth = inject(AuthService);
  private readonly dashboard = inject(DashboardService);

  readonly canSales = this.auth.has('SALE_VIEW');
  readonly canPurchases = this.auth.has('PURCHASE_VIEW');
  readonly canStock = this.auth.has('STOCK_VIEW');
  /** Someone whose role reaches none of the three areas has nothing to show here. */
  readonly nothingToShow = !this.canSales && !this.canPurchases && !this.canStock;

  readonly sales = signal<SalesFigures | null>(null);
  readonly purchases = signal<PurchaseFigures | null>(null);
  readonly stock = signal<StockFigures | null>(null);
  readonly salesFailed = signal(false);
  readonly purchasesFailed = signal(false);
  readonly stockFailed = signal(false);

  readonly salesKpis = computed<Kpi[]>(() => {
    const figures = this.sales();
    return !figures ? [] : [
      { label: 'Sales today', icon: 'pi pi-shopping-cart', value: figures.today, format: 'amount', note: 'Invoices minus credit notes' },
      { label: 'Sales this month', icon: 'pi pi-calendar', value: figures.month, format: 'amount', note: 'Net of credit notes' },
      { label: 'Unpaid invoices', icon: 'pi pi-wallet', value: figures.unpaid.amount, format: 'amount', note: countOf(figures.unpaid.count, 'invoice') },
      { label: 'Overdue', icon: 'pi pi-exclamation-circle', value: figures.overdue.amount, format: 'amount', note: countOf(figures.overdue.count, 'late invoice'), tone: figures.overdue.count > 0 ? 'danger' : undefined }
    ];
  });

  readonly purchaseKpis = computed<Kpi[]>(() => {
    const figures = this.purchases();
    return !figures ? [] : [
      { label: 'Purchases today', icon: 'pi pi-truck', value: figures.today, format: 'amount', note: 'Invoices minus supplier credit notes' },
      { label: 'Purchases this month', icon: 'pi pi-calendar', value: figures.month, format: 'amount', note: 'Net of credit notes' },
      { label: 'To pay', icon: 'pi pi-wallet', value: figures.unpaid.amount, format: 'amount', note: countOf(figures.unpaid.count, 'invoice') },
      { label: 'Overdue payables', icon: 'pi pi-exclamation-circle', value: figures.overdue.amount, format: 'amount', note: countOf(figures.overdue.count, 'late invoice'), tone: figures.overdue.count > 0 ? 'danger' : undefined }
    ];
  });

  readonly stockKpis = computed<Kpi[]>(() => {
    const figures = this.stock();
    return !figures ? [] : [
      { label: 'Stock value', icon: 'pi pi-box', value: figures.value, format: 'amount', note: 'At purchase price' },
      { label: 'Low stock', icon: 'pi pi-exclamation-triangle', value: figures.lowStockCount, format: 'count', note: 'Goods at or under their minimum', tone: figures.lowStockCount > 0 ? 'warn' : undefined }
    ];
  });

  readonly salesBars = computed<BarPoint[]>(() => barsOf(this.sales()?.months));
  readonly purchaseBars = computed<BarPoint[]>(() => barsOf(this.purchases()?.months));

  constructor() {
    if (this.canSales) {
      this.dashboard.sales().subscribe({ next: figures => this.sales.set(figures), error: () => this.salesFailed.set(true) });
    }
    if (this.canPurchases) {
      this.dashboard.purchases().subscribe({ next: figures => this.purchases.set(figures), error: () => this.purchasesFailed.set(true) });
    }
    if (this.canStock) {
      this.dashboard.stock().subscribe({ next: figures => this.stock.set(figures), error: () => this.stockFailed.set(true) });
    }
  }
}

function countOf(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`;
}

/** "2026-09" → "Sep": the bars sit under short month names, oldest first, as the server sends them. */
function barsOf(months?: MonthAmount[]): BarPoint[] {
  return (months ?? []).map(({ month, amount }) => {
    const [year, number] = month.split('-').map(Number);
    return { label: new Date(year, number - 1, 1).toLocaleString('en', { month: 'short' }), value: amount };
  });
}
