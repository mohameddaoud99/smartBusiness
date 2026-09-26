import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService } from 'primeng/api';

import { SupplierFormComponent } from './supplier-form.component';
import { SupplierResponse } from '../supplier.model';

describe('SupplierFormComponent', () => {

  let fixture: ComponentFixture<SupplierFormComponent>;
  let component: SupplierFormComponent;
  let httpMock: HttpTestingController;

  const url = 'http://localhost:8080/api/suppliers';

  const existing: SupplierResponse = {
    id: 5,
    type: 'INDIVIDUAL',
    reference: 'F-0005',
    name: 'Karim Jebali',
    nationalId: '09876543',
    birthDate: '1985-03-20',
    email: 'karim@example.tn',
    phone: '+216 20 000 000',
    billingAddress: { city: 'Sfax', country: 'Tunisia' },
    createdAt: '2026-01-01T10:00:00',
    updatedAt: '2026-01-01T10:00:00'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupplierFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        MessageService
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SupplierFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('starts invalid because the name is required', () => {
    component.open();
    expect(component.form.valid).toBeFalse();
    expect(component.form.controls['name'].hasError('required')).toBeTrue();
  });

  it('defaults to a business supplier with Tunisia as the billing country', () => {
    component.open();
    expect(component.form.value.type).toBe('COMPANY');
    expect(component.isCompany).toBeTrue();
    expect(component.nameLabel).toBe('Business name');
    expect(component.form.value.billingAddress.country).toBe('Tunisia');
  });

  it('switches the name label for an individual', () => {
    component.open();
    component.form.controls['type'].setValue('INDIVIDUAL');
    expect(component.nameLabel).toBe('Full name');
  });

  it('prefills identity, dates and address when editing', () => {
    component.open(existing);
    expect(component.editingId()).toBe(5);
    expect(component.form.value.name).toBe('Karim Jebali');
    expect(component.form.value.nationalId).toBe('09876543');
    expect(component.form.value.birthDate instanceof Date).toBeTrue();
    expect(component.form.value.billingAddress.city).toBe('Sfax');
    expect(component.form.value.sameShipping).toBeTrue();
  });

  it('unticks "same shipping" when the supplier has a distinct shipping address', () => {
    component.open({ ...existing, shippingAddress: { city: 'Tunis' } });
    expect(component.form.value.sameShipping).toBeFalse();
  });

  it('rejects an invalid email', () => {
    component.open();
    component.form.controls['email'].setValue('nope');
    expect(component.form.controls['email'].hasError('email')).toBeTrue();
  });

  it('does not call the API while the form is invalid', () => {
    component.open();
    component.submit();
    httpMock.expectNone(url);
    expect(component.form.touched).toBeTrue();
  });

  it('POSTs a company supplier without individual-only fields', () => {
    component.open();
    component.form.patchValue({ name: 'HAMMAMET SUD', taxId: '111', nationalId: 'should-be-dropped' });

    component.submit();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.type).toBe('COMPANY');
    expect(req.request.body.taxId).toBe('111');
    expect(req.request.body.nationalId).toBeUndefined();
    expect(req.request.body.birthDate).toBeNull();
    req.flush({ ...existing });
  });

  it('POSTs an individual with the birth date as an ISO string', () => {
    component.open();
    component.form.patchValue({ type: 'INDIVIDUAL', name: 'Karim', birthDate: new Date(1985, 2, 20) });

    component.submit();

    const req = httpMock.expectOne(url);
    expect(req.request.body.birthDate).toBe('1985-03-20');
    expect(req.request.body.nationalId).toBeUndefined();
    req.flush({ ...existing });
  });

  it('sends a null shipping address when it matches billing', () => {
    component.open();
    component.form.patchValue({ name: 'HAMMAMET SUD' });
    component.form.get('billingAddress')!.patchValue({ city: 'Tunis' });

    component.submit();

    const req = httpMock.expectOne(url);
    expect(req.request.body.billingAddress.city).toBe('Tunis');
    expect(req.request.body.shippingAddress).toBeNull();
    req.flush({ ...existing });
  });

  it('sends a null billing address when nothing but the default country is set', () => {
    component.open();
    component.form.patchValue({ name: 'HAMMAMET SUD' });

    component.submit();

    const req = httpMock.expectOne(url);
    expect(req.request.body.billingAddress).toBeNull();
    req.flush({ ...existing });
  });

  it('PUTs when editing', () => {
    component.open(existing);
    component.form.controls['name'].setValue('Karim B. Jebali');

    component.submit();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.method).toBe('PUT');
    req.flush({ ...existing });
  });

  it('releases the saving state on an API error', () => {
    component.open();
    component.form.patchValue({ name: 'HAMMAMET SUD' });
    component.submit();
    expect(component.saving()).toBeTrue();

    httpMock.expectOne(url).flush(
      { message: 'A supplier with this reference already exists' },
      { status: 409, statusText: 'Conflict' }
    );

    expect(component.saving()).toBeFalse();
  });
});
