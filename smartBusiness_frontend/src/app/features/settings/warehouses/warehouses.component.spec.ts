import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';

import { WarehousesComponent } from './warehouses.component';
import { WarehouseResponse } from './warehouse.model';
import { AuthService } from '../../../core/auth/auth.service';

describe('WarehousesComponent', () => {

  const url = 'http://localhost:8080/api/warehouses';

  let fixture: ComponentFixture<WarehousesComponent>;
  let component: WarehousesComponent;
  let httpMock: HttpTestingController;

  const main: WarehouseResponse = {
    id: 1, name: 'Default warehouse', defaultWarehouse: true, active: true, createdAt: '', updatedAt: ''
  };
  const annex: WarehouseResponse = {
    id: 2, name: 'Annex', address: 'Sfax', defaultWarehouse: false, active: false, createdAt: '', updatedAt: ''
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WarehousesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        MessageService,
        ConfirmationService,
        { provide: AuthService, useValue: { has: () => true, hasAny: () => true } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(WarehousesComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  function answer(list: WarehouseResponse[]) {
    httpMock.expectOne(url).flush(list);
    fixture.detectChanges();
  }

  it('lists the warehouses, marking the default and the inactive ones', () => {
    answer([main, annex]);

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Default');
    expect(rows[1].textContent).toContain('Sfax');
    expect(rows[1].textContent).toContain('Inactive');
  });

  it('cannot delete the default warehouse: its row has no delete button', () => {
    answer([main, annex]);

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows[0].querySelector('.pi-trash')).toBeNull();
    expect(rows[1].querySelector('.pi-trash')).not.toBeNull();
  });

  it('deletes a warehouse once the user confirms, then reloads', () => {
    answer([main, annex]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.confirmDelete(annex);

    const req = httpMock.expectOne(`${url}/2`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.expectOne(url).flush([main]);
  });

  it('deletes nothing before the user confirms', () => {
    answer([main, annex]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.confirmDelete(annex);

    httpMock.expectNone(`${url}/2`);
  });

  it('shows the empty state when there is no warehouse', () => {
    answer([]);

    expect(fixture.nativeElement.querySelector('.empty-title').textContent).toContain('No warehouses yet');
  });
});
