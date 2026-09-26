import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../core/auth/auth.service';

import { SalesDocumentsComponent } from './sales-documents.component';
import { SalesDocumentSummary, SalesDocumentType } from './sales-document.model';

describe('SalesDocumentsComponent', () => {

  const url = 'http://localhost:8080/api/sales-documents';

  let fixture: ComponentFixture<SalesDocumentsComponent>;
  let component: SalesDocumentsComponent;
  let httpMock: HttpTestingController;
  let router: Router;
  let permissions: string[];

  const draft: SalesDocumentSummary = {
    id: 1, type: 'QUOTE', status: 'DRAFT', reference: null, customerId: 3, customerName: 'Client Alpha',
    issueDate: '2026-09-20', total: 37.057, createdAt: '2026-09-20T10:00:00'
  };
  const issued: SalesDocumentSummary = { ...draft, id: 2, status: 'ISSUED', reference: 'QUO-2026-00001' };

  const everything = ['SALE_UPDATE', 'SALE_CREATE', 'SALE_CANCEL', 'PURCHASE_UPDATE', 'PURCHASE_CREATE', 'PURCHASE_CANCEL'];

  function open(type: SalesDocumentType = 'QUOTE') {
    permissions = everything;
    TestBed.configureTestingModule({
      imports: [SalesDocumentsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        MessageService,
        ConfirmationService,
        { provide: AuthService, useValue: { has: (permission: string) => permissions.includes(permission) } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { documentType: type } } } }
      ]
    });
    fixture = TestBed.createComponent(SalesDocumentsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();
  }

  function answer(content: SalesDocumentSummary[]) {
    httpMock.expectOne(r => r.url === url).flush({
      content, totalElements: content.length, totalPages: 1, size: 10, number: 0
    });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('asks for the documents of its own type', () => {
    open('SALES_ORDER');

    const req = httpMock.expectOne(r => r.url === url);
    expect(req.request.params.get('type')).toBe('SALES_ORDER');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('shows the empty state when there is nothing yet', () => {
    open();
    answer([]);

    expect(fixture.nativeElement.querySelector('.empty-title').textContent).toContain('No quotes yet');
  });

  it('lists the documents, showing a draft as not numbered yet', () => {
    open();
    answer([draft, issued]);

    const rows = fixture.nativeElement.querySelectorAll('.document-row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Not numbered yet');
    expect(rows[1].textContent).toContain('QUO-2026-00001');
  });

  it('sends the status filter and goes back to the first page', () => {
    open();
    answer([]);

    component.statusFilter = 'ISSUED';
    component.onFilterChange();

    const req = httpMock.expectOne(r => r.url === url);
    expect(req.request.params.get('status')).toBe('ISSUED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('opens the editor of a document, and a blank one to create', () => {
    open();
    answer([draft]);

    component.open(draft);
    component.openCreate();

    expect(router.navigate).toHaveBeenCalledWith(['/quotes', 1]);
    expect(router.navigate).toHaveBeenCalledWith(['/quotes', 'new']);
  });

  it('offers to delete a draft only', () => {
    open();
    answer([draft, issued]);

    component.buildRowMenu(draft);
    expect(component.rowMenuItems.map(item => item.label)).toContain('Delete draft');

    component.buildRowMenu(issued);
    expect(component.rowMenuItems.map(item => item.label)).not.toContain('Delete draft');
  });

  it('deletes a draft once the user confirms, then reloads', () => {
    open();
    answer([draft]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.confirmDelete(draft);

    const req = httpMock.expectOne(`${url}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.expectOne(r => r.url === url).flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('offers to print any document from its row, draft or not', () => {
    open();
    answer([draft, issued]);
    const opened = spyOn(window, 'open').and.returnValue(null);

    component.buildRowMenu(draft);
    expect(component.rowMenuItems.map(item => item.label)).toContain('Print');
    component.rowMenuItems.find(item => item.label === 'Print')!.command!({} as never);

    expect(opened).toHaveBeenCalledWith('/print/sales/1', '_blank');
  });

  // ----- Steps of the document's life, from the row menu -----

  function labels(): (string | undefined)[] {
    return component.rowMenuItems.filter(item => !item.separator).map(item => item.label);
  }

  const order: SalesDocumentSummary = { ...issued, id: 3, type: 'SALES_ORDER', reference: 'SO-2026-00001' };

  it('offers to issue a draft, next to deleting it', () => {
    open();
    answer([draft]);

    component.buildRowMenu(draft);

    expect(labels()).toEqual(['Open', 'Print', 'Issue', 'Delete draft']);
  });

  it('offers the steps of an issued quote, a confirmed order and a rejected quote', () => {
    open();
    answer([issued]);

    component.buildRowMenu(issued);
    expect(labels()).toEqual(['Open', 'Print', 'Accept', 'Reject', 'Convert to order', 'Create invoice']);

    component.buildRowMenu({ ...issued, status: 'REJECTED' });
    expect(labels()).toEqual(['Open', 'Print', 'Accept', 'Back to issued']);

    component.buildRowMenu({ ...order, status: 'CONFIRMED' });
    expect(labels()).toEqual(['Open', 'Print', 'Create delivery note', 'Create invoice', 'Cancel order']);

    component.buildRowMenu({ ...order, status: 'CANCELLED' });
    expect(labels()).toEqual(['Open', 'Print']);
  });

  it('shows only the steps the user is allowed to take', () => {
    open();
    answer([issued]);
    permissions = ['SALE_UPDATE'];

    component.buildRowMenu(issued);
    expect(labels()).toEqual(['Open', 'Print', 'Accept', 'Reject']); // no convert: it needs SALE_CREATE

    component.buildRowMenu({ ...order, status: 'ISSUED' });
    expect(labels()).toEqual(['Open', 'Print', 'Confirm']); // no cancel: it needs SALE_CANCEL

    permissions = [];
    component.buildRowMenu(draft);
    expect(labels()).toEqual(['Open', 'Print']);
  });

  it('paints what cannot be taken back in red', () => {
    open();
    answer([order]);

    component.buildRowMenu({ ...order, status: 'ISSUED' });

    const cancel = component.rowMenuItems.find(item => item.label === 'Cancel order')!;
    expect(cancel.styleClass).toBe('menu-danger');
    expect(component.rowMenuItems.find(item => item.label === 'Confirm')!.styleClass).toBeUndefined();
  });

  it('asks before any step, and changes nothing when the user declines', () => {
    open();
    answer([issued]);
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.buildRowMenu(issued);
    component.rowMenuItems.find(item => item.label === 'Accept')!.command!({} as never);

    expect(confirm).toHaveBeenCalledTimes(1);
    expect(confirm.calls.mostRecent().args[0].message).toContain('QUO-2026-00001');
    httpMock.expectNone(`${url}/2/status`);
  });

  it('accepts a quote from the list once confirmed, then reloads the list', () => {
    open();
    answer([issued]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(issued);
    component.rowMenuItems.find(item => item.label === 'Accept')!.command!({} as never);

    const req = httpMock.expectOne(`${url}/2/status`);
    expect(req.request.body).toEqual({ status: 'ACCEPTED' });
    req.flush({ ...issued, status: 'ACCEPTED' });
    httpMock.expectOne(r => r.url === url).flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('issues a draft from the list without opening it', () => {
    open();
    answer([draft]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(draft);
    component.rowMenuItems.find(item => item.label === 'Issue')!.command!({} as never);

    httpMock.expectOne(`${url}/1/issue`).flush({ ...draft, status: 'ISSUED', reference: 'QUO-2026-00003' });
    httpMock.expectOne(r => r.url === url).flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('converts a quote from the list and opens the new order', () => {
    open();
    answer([issued]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(issued);
    component.rowMenuItems.find(item => item.label === 'Convert to order')!.command!({} as never);

    httpMock.expectOne(`${url}/2/convert-to-order`).flush({ ...order, id: 9 });
    expect(router.navigate).toHaveBeenCalledWith(['/sales-orders', 9]);
  });

  it('creates a delivery note from a confirmed order and opens it', () => {
    open('SALES_ORDER');
    answer([order]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu({ ...order, status: 'CONFIRMED' });
    component.rowMenuItems.find(item => item.label === 'Create delivery note')!.command!({} as never);

    httpMock.expectOne(`${url}/3/convert-to-delivery-note`).flush({ ...order, id: 12, type: 'DELIVERY_NOTE' });
    expect(router.navigate).toHaveBeenCalledWith(['/delivery-notes', 12]);
  });

  // ----- Invoices -----

  const invoice: SalesDocumentSummary = {
    ...order, id: 6, type: 'INVOICE', status: 'ISSUED', reference: 'INV-2026-00001', total: 50, paidAmount: 0, balance: 50
  };

  it('creates an invoice from a delivered note and opens it', () => {
    open('DELIVERY_NOTE');
    const note: SalesDocumentSummary = { ...order, id: 4, type: 'DELIVERY_NOTE', status: 'DELIVERED', reference: 'BL-2026-00001' };
    answer([note]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(note);
    expect(labels()).toEqual(['Open', 'Print', 'Create invoice', 'Create return note', 'Cancel delivery note']);
    component.rowMenuItems.find(item => item.label === 'Create invoice')!.command!({} as never);

    httpMock.expectOne(`${url}/4/convert-to-invoice`).flush({ ...invoice, id: 21 });
    expect(router.navigate).toHaveBeenCalledWith(['/invoices', 21]);
  });

  it('shows the balance of an invoice, and its status as unpaid, partly paid or paid', () => {
    open('INVOICE');
    answer([invoice, { ...invoice, id: 7, status: 'PARTIALLY_PAID', paidAmount: 20, balance: 30 }, { ...invoice, id: 8, status: 'PAID', paidAmount: 50, balance: 0 }]);

    const text = (fixture.nativeElement as HTMLElement).textContent!;
    expect(text).toContain('Balance');
    expect(text).toContain('50.000');
    expect(text).toContain('Unpaid');
    expect(text).toContain('Partially paid');
    expect(text).toContain('Paid');
    expect(component.statusOptions.map(option => option.label)).toEqual(['Draft', 'Unpaid', 'Partially paid', 'Paid', 'Cancelled']);
  });

  it('offers "Add payment" on an unpaid or partly paid invoice, and only to someone who can update sales', () => {
    open('INVOICE');
    answer([invoice]);

    component.buildRowMenu(invoice);
    expect(labels()).toEqual(['Open', 'Print', 'Add payment', 'Create credit note', 'Create return note', 'Cancel invoice']);

    component.buildRowMenu({ ...invoice, status: 'PARTIALLY_PAID', paidAmount: 20, balance: 30 });
    expect(labels()).toEqual(['Open', 'Print', 'Add payment', 'Create credit note', 'Create return note']);

    component.buildRowMenu({ ...invoice, status: 'PAID', paidAmount: 50, balance: 0 });
    expect(labels()).toEqual(['Open', 'Print', 'Create credit note', 'Create return note']);

    permissions = ['SALE_CANCEL'];
    component.buildRowMenu(invoice);
    expect(labels()).toEqual(['Open', 'Print', 'Cancel invoice']);
  });

  it('opens the payment dialog on the balance of the invoice, and reloads the list once a payment is recorded', () => {
    open('INVOICE');
    answer([invoice]);
    const dialog = spyOn(component.paymentForm!, 'open');

    component.buildRowMenu({ ...invoice, status: 'PARTIALLY_PAID', paidAmount: 20, balance: 30 });
    component.rowMenuItems.find(item => item.label === 'Add payment')!.command!({} as never);

    expect(dialog).toHaveBeenCalledWith({ id: 6, reference: 'INV-2026-00001', balance: 30 });

    component.paymentForm!.saved.emit();
    httpMock.expectOne(r => r.url === url).flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('creates a credit note from an invoice and opens it', () => {
    open('INVOICE');
    answer([invoice]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(invoice);
    component.rowMenuItems.find(item => item.label === 'Create credit note')!.command!({} as never);

    httpMock.expectOne(`${url}/6/convert-to-credit-note`).flush({ ...invoice, id: 31, type: 'CREDIT_NOTE' });
    expect(router.navigate).toHaveBeenCalledWith(['/credit-notes', 31]);
  });

  it('has no "New" button on the credit notes: they are made from an invoice', () => {
    open('CREDIT_NOTE');
    answer([]);

    const page = (fixture.nativeElement as HTMLElement).textContent!;
    expect(page).not.toContain('New credit note');
    expect(page).toContain('made from an issued invoice');
  });

  it('shows an issued credit note as applied, with the actions to open, print and cancel it', () => {
    open('CREDIT_NOTE');
    const note: SalesDocumentSummary = { ...invoice, id: 7, type: 'CREDIT_NOTE', reference: 'CN-2026-00001' };
    answer([note]);

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Applied');
    component.buildRowMenu(note);
    expect(labels()).toEqual(['Open', 'Print', 'Cancel credit note']);
  });

  it('offers to deliver an issued delivery note, and to cancel it or a delivered one', () => {
    open('DELIVERY_NOTE');
    const note: SalesDocumentSummary = { ...order, id: 4, type: 'DELIVERY_NOTE', reference: 'BL-2026-00001' };
    answer([note]);

    component.buildRowMenu({ ...note, status: 'ISSUED' });
    expect(labels()).toEqual(['Open', 'Print', 'Mark as delivered', 'Cancel delivery note']);

    component.buildRowMenu({ ...note, status: 'DELIVERED' });
    expect(labels()).toEqual(['Open', 'Print', 'Create invoice', 'Create return note', 'Cancel delivery note']);
  });

  // ----- Return notes -----

  it('creates a return note from a delivered note and opens it', () => {
    open('DELIVERY_NOTE');
    const note: SalesDocumentSummary = { ...order, id: 4, type: 'DELIVERY_NOTE', status: 'DELIVERED', reference: 'BL-2026-00001' };
    answer([note]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(note);
    component.rowMenuItems.find(item => item.label === 'Create return note')!.command!({} as never);

    httpMock.expectOne(`${url}/4/convert-to-return-note`).flush({ ...note, id: 41, type: 'RETURN_NOTE', status: 'DRAFT' });
    expect(router.navigate).toHaveBeenCalledWith(['/return-notes', 41]);
  });

  it('can start a return note by hand, and shows an issued one as received, with the actions to open, print and cancel it', () => {
    open('RETURN_NOTE');
    const note: SalesDocumentSummary = { ...order, id: 7, type: 'RETURN_NOTE', status: 'ISSUED', reference: 'RN-2026-00001' };
    answer([note]);

    const page = (fixture.nativeElement as HTMLElement).textContent!;
    expect(page).toContain('New return note');
    expect(page).toContain('Received');
    component.buildRowMenu(note);
    expect(labels()).toEqual(['Open', 'Print', 'Cancel return note']);
    component.openCreate();
    expect(router.navigate).toHaveBeenCalledWith(['/return-notes', 'new']);
  });
});
