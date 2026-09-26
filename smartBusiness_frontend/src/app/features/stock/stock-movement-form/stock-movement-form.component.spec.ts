import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService } from 'primeng/api';

import { StockMovementFormComponent } from './stock-movement-form.component';
import { AuthService } from '../../../core/auth/auth.service';
import { WarehouseResponse } from '../../settings/warehouses/warehouse.model';

describe('StockMovementFormComponent', () => {

  const api = 'http://localhost:8080/api';

  let fixture: ComponentFixture<StockMovementFormComponent>;
  let component: StockMovementFormComponent;
  let httpMock: HttpTestingController;
  let permissions: string[];

  function warehouse(id: number, name: string, active = true): WarehouseResponse {
    return { id, name, defaultWarehouse: id === 1, active, createdAt: '', updatedAt: '' };
  }

  /** Builds the dialog for a user holding the given permissions and answers what open() fetches. */
  function open(granted: string[], warehouses: WarehouseResponse[], productId?: number) {
    permissions = granted;
    TestBed.configureTestingModule({
      imports: [StockMovementFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        MessageService,
        { provide: AuthService, useValue: { has: (p: string) => permissions.includes(p), hasAny: () => true } }
      ]
    });
    fixture = TestBed.createComponent(StockMovementFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    component.open(productId);
    httpMock.expectOne(r => r.url === `${api}/products`)
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 500, number: 0 });
    httpMock.expectOne(`${api}/warehouses`).flush(warehouses);
  }

  afterEach(() => httpMock.verify());

  it('offers every movement to someone who may adjust and transfer', () => {
    open(['STOCK_ADJUST', 'STOCK_TRANSFER'], [warehouse(1, 'Main'), warehouse(2, 'Annex')]);

    expect(component.actionOptions().map(o => o.value)).toEqual(['ENTRY', 'EXIT', 'ADJUSTMENT', 'TRANSFER']);
  });

  it('hides what the user is not allowed to do', () => {
    open(['STOCK_TRANSFER'], [warehouse(1, 'Main'), warehouse(2, 'Annex')]);
    expect(component.actionOptions().map(o => o.value)).toEqual(['TRANSFER']);
    expect(component.form.value.type).toBe('TRANSFER');
  });

  it('only offers active warehouses', () => {
    open(['STOCK_ADJUST'], [warehouse(1, 'Main'), warehouse(2, 'Old', false), warehouse(3, 'Annex')]);

    expect(component.warehouses().map(w => w.name)).toEqual(['Main', 'Annex']);
  });

  it('picks the warehouse for you when there is only one', () => {
    open(['STOCK_ADJUST'], [warehouse(1, 'Main')]);

    expect(component.form.value.warehouseId).toBe(1);
  });

  it('starts on the product it was opened for', () => {
    open(['STOCK_ADJUST'], [warehouse(1, 'Main'), warehouse(2, 'Annex')], 20);

    expect(component.form.value.productId).toBe(20);
  });

  it('does not call the API while the form is invalid', () => {
    open(['STOCK_ADJUST'], [warehouse(1, 'Main'), warehouse(2, 'Annex')]);

    component.submit();

    httpMock.expectNone(`${api}/stock/movements`);
    expect(component.form.touched).toBeTrue();
  });

  it('POSTs an entry to the movements endpoint', () => {
    open(['STOCK_ADJUST'], [warehouse(1, 'Main'), warehouse(2, 'Annex')]);
    let saved = false;
    component.saved.subscribe(() => saved = true);
    component.form.patchValue({ productId: 20, warehouseId: 1, quantity: 10, reason: '  Delivery ' });

    component.submit();

    const req = httpMock.expectOne(`${api}/stock/movements`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      type: 'ENTRY', productId: 20, warehouseId: 1, quantity: 10, reason: 'Delivery'
    });
    req.flush({});
    expect(saved).toBeTrue();
    expect(component.visible()).toBeFalse();
  });

  it('asks for a destination on a transfer, and POSTs to the transfers endpoint', () => {
    open(['STOCK_ADJUST', 'STOCK_TRANSFER'], [warehouse(1, 'Main'), warehouse(2, 'Annex')]);
    component.form.patchValue({ type: 'TRANSFER', productId: 20, warehouseId: 1, quantity: 3 });

    expect(component.isTransfer).toBeTrue();
    expect(component.form.valid).toBeFalse(); // no destination yet

    component.form.patchValue({ toWarehouseId: 2 });
    component.submit();

    const req = httpMock.expectOne(`${api}/stock/transfers`);
    expect(req.request.body).toEqual({
      productId: 20, fromWarehouseId: 1, toWarehouseId: 2, quantity: 3, reason: null
    });
    req.flush([]);
  });

  it('accepts a count of zero for an adjustment, but not an entry of zero', () => {
    open(['STOCK_ADJUST'], [warehouse(1, 'Main'), warehouse(2, 'Annex')]);
    component.form.patchValue({ productId: 20, warehouseId: 1, quantity: 0 });
    expect(component.form.valid).toBeFalse();

    component.form.patchValue({ type: 'ADJUSTMENT' });
    expect(component.isAdjustment).toBeTrue();
    expect(component.form.valid).toBeTrue();
  });

  it('releases the saving state on an API error', () => {
    open(['STOCK_ADJUST'], [warehouse(1, 'Main'), warehouse(2, 'Annex')]);
    component.form.patchValue({ type: 'EXIT', productId: 20, warehouseId: 1, quantity: 99 });

    component.submit();
    expect(component.saving()).toBeTrue();
    httpMock.expectOne(`${api}/stock/movements`)
      .flush({ message: 'Not enough stock' }, { status: 422, statusText: 'Unprocessable' });

    expect(component.saving()).toBeFalse();
    expect(component.visible()).toBeTrue();
  });
});
