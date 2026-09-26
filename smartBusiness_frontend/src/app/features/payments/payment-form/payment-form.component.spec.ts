import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService } from 'primeng/api';

import { PaymentFormComponent } from './payment-form.component';
import { NotificationService } from '../../../core/services/notification.service';

describe('PaymentFormComponent', () => {

  const url = 'http://localhost:8080/api/payments';
  const invoice = { id: 5, reference: 'INV-2026-00001', balance: 37.057 };

  let fixture: ComponentFixture<PaymentFormComponent>;
  let component: PaymentFormComponent;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PaymentFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), MessageService]
    });
    fixture = TestBed.createComponent(PaymentFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('opens on the whole balance, today, cash', () => {
    component.open(invoice);

    expect(component.visible()).toBeTrue();
    expect(component.form.value.amount).toBe(37.057);
    expect(component.form.value.method).toBe('CASH');
    expect((component.form.value.paymentDate as Date).toDateString()).toBe(new Date().toDateString());
  });

  it('refuses an amount of zero, and one above what is still due', () => {
    component.open(invoice);
    const amount = component.form.controls['amount'];

    amount.setValue(0);
    expect(amount.hasError('min')).toBeTrue();
    amount.setValue(37.058);
    expect(amount.hasError('max')).toBeTrue();
    expect(component.errorFor('amount')).toContain('more than what is still due');
    amount.setValue(37.057);
    expect(amount.valid).toBeTrue();
  });

  it('follows the balance of the invoice it is opened on', () => {
    component.open({ ...invoice, balance: 10 });
    expect(component.form.controls['amount'].hasError('max')).toBeFalse();

    component.form.controls['amount'].setValue(10.001);
    expect(component.form.controls['amount'].hasError('max')).toBeTrue();

    component.open({ ...invoice, balance: 20 });
    component.form.controls['amount'].setValue(15);
    expect(component.form.controls['amount'].valid).toBeTrue();
  });

  it('records the payment with an ISO date and trimmed text, then closes and says so', () => {
    const success = spyOn(TestBed.inject(NotificationService), 'success');
    let saved = 0;
    component.saved.subscribe(() => saved++);
    component.open(invoice);
    component.form.patchValue({
      amount: 20, paymentDate: new Date(2026, 8, 21), method: 'CHECK', reference: '  CHQ-77  ', notes: '  '
    });

    component.submit();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      invoiceId: 5, amount: 20, paymentDate: '2026-09-21', method: 'CHECK', reference: 'CHQ-77', notes: null
    });
    req.flush({ id: 1 });

    expect(success).toHaveBeenCalledWith('Payment recorded.');
    expect(saved).toBe(1);
    expect(component.visible()).toBeFalse();
    expect(component.saving()).toBeFalse();
  });

  it('pays a supplier through the supplier payments, and titles the dialog accordingly', () => {
    fixture.componentRef.setInput('family', 'supplier');
    fixture.detectChanges();
    component.open(invoice);

    component.submit();

    httpMock.expectOne('http://localhost:8080/api/supplier-payments').flush({ id: 1 });
    httpMock.expectNone(url);
  });

  it('sends nothing while the form is invalid', () => {
    component.open(invoice);
    component.form.patchValue({ amount: null });

    component.submit();

    httpMock.expectNone(url);
    expect(component.form.controls['amount'].touched).toBeTrue();
    expect(component.visible()).toBeTrue();
  });

  it('stays open, and stops saving, when the server refuses', () => {
    component.open(invoice);

    component.submit();
    httpMock.expectOne(url).flush({ message: 'This invoice is already paid in full' }, { status: 422, statusText: 'Unprocessable' });

    expect(component.saving()).toBeFalse();
    expect(component.visible()).toBeTrue();
  });
});
