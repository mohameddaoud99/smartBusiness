import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService } from 'primeng/api';

import { WarehouseFormComponent } from './warehouse-form.component';
import { WarehouseResponse } from '../warehouse.model';

describe('WarehouseFormComponent', () => {

  const url = 'http://localhost:8080/api/warehouses';

  let fixture: ComponentFixture<WarehouseFormComponent>;
  let component: WarehouseFormComponent;
  let httpMock: HttpTestingController;

  const existing: WarehouseResponse = {
    id: 2, name: 'Annex', address: 'Sfax', defaultWarehouse: false, active: false, createdAt: '', updatedAt: ''
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WarehouseFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), MessageService]
    }).compileComponents();

    fixture = TestBed.createComponent(WarehouseFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('starts invalid because the name is required, and active by default', () => {
    component.open();

    expect(component.form.controls['name'].hasError('required')).toBeTrue();
    expect(component.form.value.active).toBeTrue();
  });

  it('does not call the API while the form is invalid', () => {
    component.open();

    component.submit();

    httpMock.expectNone(url);
    expect(component.form.touched).toBeTrue();
  });

  it('POSTs a new warehouse, sending a blank address as null', () => {
    component.open();
    component.form.patchValue({ name: 'Annex', address: '   ' });

    component.submit();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Annex', address: null, active: true });
    req.flush(existing);
  });

  it('prefills and PUTs when editing', () => {
    component.open(existing);
    expect(component.form.value.name).toBe('Annex');
    expect(component.form.getRawValue().active).toBeFalse();

    component.form.patchValue({ address: 'Sousse' });
    component.submit();

    const req = httpMock.expectOne(`${url}/2`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.address).toBe('Sousse');
    req.flush(existing);
  });

  it('locks the active switch on the default warehouse', () => {
    component.open({ ...existing, defaultWarehouse: true, active: true });
    expect(component.form.controls['active'].disabled).toBeTrue();

    component.open();
    expect(component.form.controls['active'].enabled).toBeTrue();
  });

  it('releases the saving state on an API error', () => {
    component.open();
    component.form.patchValue({ name: 'Annex' });
    component.submit();

    httpMock.expectOne(url).flush({ message: 'exists' }, { status: 409, statusText: 'Conflict' });

    expect(component.saving()).toBeFalse();
  });
});
