import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';

import { StockMovementsComponent } from './stock-movements.component';
import { StockMovement } from './stock.model';

describe('StockMovementsComponent', () => {

  const api = 'http://localhost:8080/api';

  let fixture: ComponentFixture<StockMovementsComponent>;
  let component: StockMovementsComponent;
  let httpMock: HttpTestingController;

  function movement(overrides: Partial<StockMovement> = {}): StockMovement {
    return {
      id: 1, type: 'ENTRY', productId: 20, productReference: 'P-0020', productName: 'USB-C Cable',
      warehouseId: 1, warehouseName: 'Main', quantity: 10, reason: 'Delivery',
      occurredAt: '2026-09-20T10:00:00', ...overrides
    };
  }

  function open(queryParams: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [StockMovementsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        MessageService,
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: new Map(Object.entries(queryParams)) } } }
      ]
    });
    fixture = TestBed.createComponent(StockMovementsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock.expectOne(r => r.url === `${api}/products`)
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 500, number: 0 });
    httpMock.expectOne(`${api}/warehouses`).flush([]);
  }

  function answer(content: StockMovement[]) {
    httpMock.expectOne(r => r.url === `${api}/stock/movements`)
      .flush({ content, totalElements: content.length, totalPages: 1, size: 10, number: 0 });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('shows the empty state before any movement', () => {
    open();
    answer([]);

    expect(fixture.nativeElement.querySelector('.empty-title').textContent).toContain('No stock movements yet');
  });

  it('shows entries in green with a plus and exits in red', () => {
    open();
    answer([movement(), movement({ id: 2, type: 'EXIT', quantity: -4 })]);

    const cells = fixture.nativeElement.querySelectorAll('td.num');
    expect(cells[0].textContent.trim()).toBe('+10');
    expect(cells[0].classList).toContain('is-positive');
    expect(cells[1].textContent.trim()).toBe('-4');
    expect(cells[1].classList).toContain('is-negative');
  });

  it('links a reservation back to its sales order', () => {
    open();
    answer([movement({ type: 'RESERVE', sourceType: 'SALES_DOCUMENT', sourceId: 8, reason: 'Reserved for a sales order' })]);

    const link = fixture.nativeElement.querySelector('.source-link') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/sales-orders/8');
  });

  it('starts filtered on the product it was opened from', () => {
    open({ productId: '20' });

    const req = httpMock.expectOne(r => r.url === `${api}/stock/movements`);
    expect(req.request.params.get('productId')).toBe('20');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
    expect(component.hasFilters).toBeTrue();
  });

  it('sends the type and warehouse filters and goes back to the first page', () => {
    open();
    answer([]);

    component.typeFilter = 'EXIT';
    component.warehouseFilter = 2;
    component.onFilterChange();

    const req = httpMock.expectOne(r => r.url === `${api}/stock/movements`);
    expect(req.request.params.get('type')).toBe('EXIT');
    expect(req.request.params.get('warehouseId')).toBe('2');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });
});
