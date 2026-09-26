import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';

import { DashboardComponent } from './dashboard.component';
import { PurchaseFigures, SalesFigures, StockFigures } from './dashboard.model';
import { AuthService } from '../../core/auth/auth.service';

describe('DashboardComponent', () => {

  const api = 'http://localhost:8080/api/dashboard';

  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let httpMock: HttpTestingController;

  const months = ['2026-04', '2026-05', '2026-06', '2026-07', '2026-08', '2026-09']
    .map((month, index) => ({ month, amount: index * 10 }));

  const salesFigures = (overrides: Partial<SalesFigures> = {}): SalesFigures => ({
    today: 80, month: 130, unpaid: { count: 3, amount: 140 }, overdue: { count: 1, amount: 30 },
    overdueInvoices: [{
      id: 7, type: 'INVOICE', status: 'PARTIALLY_PAID', reference: 'INV-2026-00007', customerId: 1,
      customerName: 'Client Alpha', issueDate: '2026-09-01', dueDate: '2026-09-15', total: 50, balance: 30, createdAt: ''
    }],
    months, ...overrides
  });

  const purchaseFigures = (overrides: Partial<PurchaseFigures> = {}): PurchaseFigures => ({
    today: 60, month: 90, unpaid: { count: 2, amount: 110 }, overdue: { count: 0, amount: 0 }, overdueInvoices: [], months,
    ...overrides
  });

  const stockFigures = (overrides: Partial<StockFigures> = {}): StockFigures => ({
    value: 210, lowStockCount: 1,
    lowStock: [{
      productId: 4, reference: 'P-0004', name: 'USB-C Cable', unit: 'PIECE', minStock: 5, physical: 4, reserved: 0,
      available: 4, lowStock: true
    }],
    ...overrides
  });

  function open(permissions: string[]) {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), provideRouter([]),
        { provide: AuthService, useValue: { has: (permission: string) => permissions.includes(permission) } }
      ]
    });
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  // innerText, not textContent: it puts a space between the blocks, as the eye sees them — in lower case, the
  // labels being upper-cased by the stylesheet
  const text = () => (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ').toLowerCase();

  afterEach(() => httpMock.verify());

  it('asks only for the areas the person may see', () => {
    open(['SALE_VIEW']);

    httpMock.expectOne(`${api}/sales`).flush(salesFigures());
    httpMock.expectNone(`${api}/purchases`);
    httpMock.expectNone(`${api}/stock`);
  });

  it('asks for all three areas when the person may see them all', () => {
    open(['SALE_VIEW', 'PURCHASE_VIEW', 'STOCK_VIEW']);

    httpMock.expectOne(`${api}/sales`).flush(salesFigures());
    httpMock.expectOne(`${api}/purchases`).flush(purchaseFigures());
    httpMock.expectOne(`${api}/stock`).flush(stockFigures());
    fixture.detectChanges();

    expect(text()).toContain('sales');
    expect(text()).toContain('purchases');
    expect(text()).toContain('stock');
  });

  it('says there is nothing to show, and asks for nothing, when the role reaches no area', () => {
    open(['CUSTOMER_VIEW']);

    expect(component.nothingToShow).toBeTrue();
    expect(text()).toContain('nothing to show yet');
    httpMock.expectNone(r => r.url.startsWith(api));
  });

  it('shows the sales figures the server computed, without adding anything up', () => {
    open(['SALE_VIEW']);
    httpMock.expectOne(`${api}/sales`).flush(salesFigures());
    fixture.detectChanges();

    expect(text()).toContain('sales today 80.000');
    expect(text()).toContain('sales this month 130.000');
    expect(text()).toContain('unpaid invoices 140.000');
    expect(text()).toContain('3 invoices');
    expect(text()).toContain('overdue 30.000');
    expect(text()).toContain('1 late invoice');
  });

  it('flags the overdue card only when something is late', () => {
    open(['SALE_VIEW']);
    httpMock.expectOne(`${api}/sales`).flush(salesFigures());
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.kpi.is-danger').length).toBe(1);

    component.sales.set(salesFigures({ overdue: { count: 0, amount: 0 }, overdueInvoices: [] }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.kpi.is-danger').length).toBe(0);
  });

  it('draws the last six months oldest first, under short month names', () => {
    open(['SALE_VIEW']);
    httpMock.expectOne(`${api}/sales`).flush(salesFigures());
    fixture.detectChanges();

    expect(component.salesBars().map(bar => bar.label)).toEqual(['Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep']);
    expect(component.salesBars().map(bar => bar.value)).toEqual([0, 10, 20, 30, 40, 50]);
    expect(fixture.nativeElement.querySelectorAll('app-bar-chart .bar-column').length).toBe(6);
  });

  it('lists the late invoices with a link to each, and an empty state when there is none', () => {
    open(['SALE_VIEW']);
    httpMock.expectOne(`${api}/sales`).flush(salesFigures());
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('tbody a') as HTMLAnchorElement;
    expect(link.textContent).toContain('INV-2026-00007');
    expect(link.getAttribute('href')).toBe('/invoices/7');
    expect(text()).toContain('client alpha');

    component.sales.set(salesFigures({ overdueInvoices: [] }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty-title').textContent).toContain('No late invoice');
  });

  it('shows the purchase figures, with the late supplier invoices linking to the purchase invoice', () => {
    open(['PURCHASE_VIEW']);
    httpMock.expectOne(`${api}/purchases`).flush(purchaseFigures({
      overdue: { count: 1, amount: 50 },
      overdueInvoices: [{
        id: 9, type: 'PURCHASE_INVOICE', status: 'VALIDATED', reference: 'PINV-2026-00009', supplierId: 3,
        supplierName: 'Fournisseur Beta', issueDate: '2026-09-01', dueDate: '2026-09-15', total: 60, balance: 50, createdAt: ''
      }]
    }));
    fixture.detectChanges();

    expect(text()).toContain('purchases this month 90.000');
    expect(text()).toContain('to pay 110.000');
    const link = fixture.nativeElement.querySelector('tbody a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/purchase-invoices/9');
    expect(text()).toContain('fournisseur beta');
  });

  it('shows the stock value and the goods running low, and says how many more there are', () => {
    open(['STOCK_VIEW']);
    httpMock.expectOne(`${api}/stock`).flush(stockFigures({ lowStockCount: 7 }));
    fixture.detectChanges();

    expect(text()).toContain('stock value 210.000');
    expect(text()).toContain('low stock 7');
    expect(text()).toContain('usb-c cable');
    expect(text()).toContain('and 6 more');
    expect(fixture.nativeElement.querySelectorAll('.kpi.is-warn').length).toBe(1);
  });

  it('shows an empty state when no product is running low', () => {
    open(['STOCK_VIEW']);
    httpMock.expectOne(`${api}/stock`).flush(stockFigures({ lowStockCount: 0, lowStock: [] }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-title').textContent).toContain('No product is running low');
    expect(text()).not.toContain('and 0 more');
  });

  it('says so when an area cannot be loaded, and still shows the others', () => {
    open(['SALE_VIEW', 'STOCK_VIEW']);
    httpMock.expectOne(`${api}/sales`).flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    httpMock.expectOne(`${api}/stock`).flush(stockFigures());
    fixture.detectChanges();

    expect(text()).toContain('the sales figures could not be loaded.');
    expect(text()).toContain('stock value 210.000');
  });
});
