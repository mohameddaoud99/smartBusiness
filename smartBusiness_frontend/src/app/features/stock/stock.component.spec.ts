import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';

import { StockComponent } from './stock.component';
import { StockLevel } from './stock.model';
import { AuthService } from '../../core/auth/auth.service';

describe('StockComponent', () => {

  const api = 'http://localhost:8080/api';

  let fixture: ComponentFixture<StockComponent>;
  let component: StockComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  function level(overrides: Partial<StockLevel> = {}): StockLevel {
    return {
      productId: 20, reference: 'P-0020', name: 'USB-C Cable', unit: 'PIECE', minStock: 5,
      physical: 10, reserved: 6, available: 4, lowStock: true, ...overrides
    };
  }

  function empty() {
    return { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StockComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        MessageService,
        { provide: AuthService, useValue: { has: () => true, hasAny: () => true } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StockComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();

    httpMock.expectOne(`${api}/warehouses`).flush([]);
  });

  function answer(content: StockLevel[]) {
    httpMock.expectOne(r => r.url === `${api}/stock/levels`)
      .flush({ content, totalElements: content.length, totalPages: 1, size: 10, number: 0 });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('shows the empty state when no good is stocked', () => {
    answer([]);

    expect(fixture.nativeElement.querySelector('.empty-title').textContent).toContain('No stocked products yet');
  });

  it('lists what is in stock, reserved and available, and flags a low product', () => {
    answer([level(), level({ productId: 21, name: 'Adapter', reference: 'P-0021', lowStock: false, available: 40, physical: 40, reserved: 0 })]);

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('USB-C Cable');
    expect(rows[0].textContent).toContain('Low stock');
    expect(rows[1].textContent).not.toContain('Low stock');
  });

  it('flags an oversold product (negative available) that has no minimum', () => {
    answer([level({ lowStock: false, minStock: null, physical: 0, reserved: 2, available: -2 })]);

    expect(fixture.nativeElement.querySelector('tbody tr').textContent).toContain('Oversold');
    expect(fixture.nativeElement.querySelector('.available').classList).toContain('is-negative');
  });

  it('asks only for the low-stock products when the filter is on', () => {
    answer([]);

    component.lowOnly = true;
    component.onFilterChange();

    const req = httpMock.expectOne(r => r.url === `${api}/stock/levels`);
    expect(req.request.params.get('lowOnly')).toBe('true');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(empty());
  });

  it('switches the low-stock filter off when a warehouse is chosen', () => {
    answer([]);
    component.lowOnly = true;

    component.warehouseFilter = 2;
    component.onWarehouseChange();

    expect(component.lowOnly).toBeFalse();
    const req = httpMock.expectOne(r => r.url === `${api}/stock/levels`);
    expect(req.request.params.get('warehouseId')).toBe('2');
    expect(req.request.params.has('lowOnly')).toBeFalse();
    req.flush(empty());
  });

  it('clears every filter', () => {
    answer([]);
    component.searchTerm = 'cable';
    component.warehouseFilter = 2;
    component.lowOnly = true;

    component.clearFilters();

    expect(component.hasFilters).toBeFalse();
    httpMock.expectOne(r => r.url === `${api}/stock/levels`).flush(empty());
  });

  it('offers a movement and the history of a product from its row', () => {
    answer([level()]);

    component.buildRowMenu(level());

    expect(component.rowMenuItems.map(i => i.label)).toEqual(['New movement', 'View movements']);
    component.rowMenuItems[1].command!({} as never);
    expect(router.navigate).toHaveBeenCalledWith(['/stock/movements'], { queryParams: { productId: 20 } });
  });
});
