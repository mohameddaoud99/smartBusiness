import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';

import { InvoicePaymentsComponent } from './invoice-payments.component';
import { AuthService } from '../../../core/auth/auth.service';
import { SalesDocumentResponse } from '../../sales/sales-document.model';
import { PaymentResponse } from '../payment.model';

describe('InvoicePaymentsComponent', () => {

  const url = 'http://localhost:8080/api/payments';

  let fixture: ComponentFixture<InvoicePaymentsComponent>;
  let component: InvoicePaymentsComponent;
  let httpMock: HttpTestingController;
  let permissions: string[];

  function invoice(overrides: Partial<SalesDocumentResponse> = {}): SalesDocumentResponse {
    return {
      id: 5, type: 'INVOICE', status: 'PARTIALLY_PAID', reference: 'INV-2026-00001', issueDate: '2026-09-20',
      subtotal: 50, total: 50, paidAmount: 20, balance: 30, lines: [], taxes: [], derived: [], ...overrides
    };
  }

  function payment(overrides: Partial<PaymentResponse> = {}): PaymentResponse {
    return {
      id: 30, invoiceId: 5, invoiceReference: 'INV-2026-00001', customerName: 'Client', amount: 20,
      paymentDate: '2026-09-21', method: 'CHECK', reference: 'CHQ-1', status: 'ACTIVE', createdAt: '', ...overrides
    };
  }

  function open(
    document: SalesDocumentResponse, payments: PaymentResponse[] = [], granted = ['SALE_UPDATE', 'SALE_CANCEL']
  ) {
    permissions = granted;
    TestBed.configureTestingModule({
      imports: [InvoicePaymentsComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), MessageService, ConfirmationService,
        { provide: AuthService, useValue: { has: (permission: string) => permissions.includes(permission) } }
      ]
    });
    fixture = TestBed.createComponent(InvoicePaymentsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('document', document);
    fixture.detectChanges();

    const req = httpMock.expectOne(r => r.url === url);
    expect(req.request.params.get('invoiceId')).toBe('5');
    req.flush({ content: payments, totalElements: payments.length, totalPages: 1, size: 100, number: 0 });
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent!;
  const buttons = () => Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
    .map(button => button.textContent?.trim() || button.getAttribute('aria-label') || '');

  afterEach(() => httpMock.verify());

  it('shows the total, what has been paid and what is left, as the server computed them', () => {
    open(invoice());

    expect(text()).toContain('50.000');
    expect(text()).toContain('20.000');
    expect(text()).toContain('30.000');
  });

  it('asks the supplier payments, and the purchase rights, for a purchase invoice', () => {
    permissions = ['PURCHASE_UPDATE', 'PURCHASE_CANCEL'];
    TestBed.configureTestingModule({
      imports: [InvoicePaymentsComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), MessageService, ConfirmationService,
        { provide: AuthService, useValue: { has: (permission: string) => permissions.includes(permission) } }
      ]
    });
    fixture = TestBed.createComponent(InvoicePaymentsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('family', 'supplier');
    fixture.componentRef.setInput('document', { ...invoice({ paidAmount: 0, balance: 50 }), status: 'VALIDATED' });
    fixture.detectChanges();

    httpMock.expectOne(r => r.url === 'http://localhost:8080/api/supplier-payments')
      .flush({ content: [payment()], totalElements: 1, totalPages: 1, size: 100, number: 0 });
    fixture.detectChanges();

    expect(component.updatePermission).toBe('PURCHASE_UPDATE');
    expect(component.cancelPermission).toBe('PURCHASE_CANCEL');
    expect(component.canAddPayment).toBeTrue();
    expect(buttons()).toContain('Add payment');
    expect(buttons()).toContain('Cancel payment');
  });

  it('shows what the credit notes took off, only when there is some', () => {
    open(invoice());
    expect(text()).not.toContain('Credited');

    fixture.componentRef.setInput('document', invoice({ total: 50, creditedAmount: 15, paidAmount: 20, balance: 15 }));
    fixture.detectChanges();
    httpMock.expectOne(r => r.url === url).flush({ content: [], totalElements: 0, totalPages: 1, size: 100, number: 0 });
    fixture.detectChanges();

    expect(text()).toContain('Credited');
    expect(text()).toContain('15.000');
  });

  it('says what the supplier owes us, not a refund, when credit notes came after the payments to a supplier', () => {
    permissions = ['PURCHASE_UPDATE', 'PURCHASE_CANCEL'];
    TestBed.configureTestingModule({
      imports: [InvoicePaymentsComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), MessageService, ConfirmationService,
        { provide: AuthService, useValue: { has: (permission: string) => permissions.includes(permission) } }
      ]
    });
    fixture = TestBed.createComponent(InvoicePaymentsComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('family', 'supplier');
    fixture.componentRef.setInput('document', { ...invoice({ total: 50, paidAmount: 50, creditedAmount: 20, balance: -20 }), status: 'PAID' });
    fixture.detectChanges();
    httpMock.expectOne(r => r.url === 'http://localhost:8080/api/supplier-payments')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 100, number: 0 });
    fixture.detectChanges();

    expect(text()).toContain('To recover');
    expect(text()).not.toContain('To refund');
  });

  it('shows what the customer is owed as a refund when credit notes came after the payments', () => {
    open(invoice({ status: 'PAID', total: 50, paidAmount: 50, creditedAmount: 20, balance: -20 }));

    expect(text()).toContain('To refund');
    expect(text()).toContain('20.000');
    expect(text()).not.toContain('-20.000');
  });

  it('lists the payments with their method, reference and status, cancelled ones included', () => {
    open(invoice(), [payment(), payment({ id: 31, amount: 5, method: 'CASH', reference: null, status: 'CANCELLED' })]);

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Cheque');
    expect(rows[0].textContent).toContain('CHQ-1');
    expect(rows[0].textContent).toContain('Recorded');
    expect(rows[1].textContent).toContain('Cash');
    expect(rows[1].textContent).toContain('Cancelled');
    expect(rows[1].classList).toContain('is-cancelled');
  });

  it('shows an empty state when nothing has been paid', () => {
    open(invoice({ status: 'ISSUED', paidAmount: 0, balance: 50 }));

    expect((fixture.nativeElement as HTMLElement).querySelector('.empty-title')!.textContent).toContain('No payment yet');
  });

  it('offers to add a payment on an unpaid or partly paid invoice only', () => {
    open(invoice({ status: 'ISSUED', paidAmount: 0, balance: 50 }));
    expect(buttons()).toContain('Add payment');
  });

  it('offers no new payment on a paid or a cancelled invoice', () => {
    open(invoice({ status: 'PAID', paidAmount: 50, balance: 0 }));
    expect(buttons()).not.toContain('Add payment');
  });

  it('hides "Add payment" and the cancel button from someone who may not use them', () => {
    open(invoice(), [payment()], ['SALE_VIEW']);

    expect(buttons()).not.toContain('Add payment');
    expect(buttons()).not.toContain('Cancel payment');
  });

  it('shows both to someone who may', () => {
    open(invoice(), [payment()]);

    expect(buttons()).toContain('Add payment');
    expect(buttons()).toContain('Cancel payment');
  });

  it('opens the payment dialog on the balance of the invoice', () => {
    open(invoice());
    const dialog = spyOn(component.paymentForm, 'open');

    component.addPayment();

    expect(dialog).toHaveBeenCalledWith({ id: 5, reference: 'INV-2026-00001', balance: 30 });
  });

  it('asks before cancelling a payment, and changes nothing when the user declines', () => {
    open(invoice(), [payment()]);
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.confirmCancel(payment());

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(confirm.calls.mostRecent().args[0].message).toContain('20.000');
    httpMock.expectNone(`${url}/30/cancel`);
  });

  it('cancels a payment once confirmed, and tells the page the invoice changed', () => {
    open(invoice(), [payment()]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());
    let changed = 0;
    component.changed.subscribe(() => changed++);

    component.confirmCancel(payment());

    httpMock.expectOne(`${url}/30/cancel`).flush(payment({ status: 'CANCELLED' }));
    expect(changed).toBe(1);
  });

  it('tells the page once a payment was recorded', () => {
    open(invoice());
    let changed = 0;
    component.changed.subscribe(() => changed++);

    component.paymentForm.saved.emit();

    expect(changed).toBe(1);
  });

  it('loads the payments again when the invoice it shows is replaced', () => {
    open(invoice());

    fixture.componentRef.setInput('document', invoice({ status: 'PAID', paidAmount: 50, balance: 0 }));
    fixture.detectChanges();

    httpMock.expectOne(r => r.url === url).flush({ content: [payment()], totalElements: 1, totalPages: 1, size: 100, number: 0 });
    expect(component.payments().length).toBe(1);
  });
});
