import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../core/auth/auth.service';

import { PurchaseDocumentsComponent } from './purchase-documents.component';
import { PurchaseDocumentSummary, PurchaseDocumentType } from './purchase-document.model';

describe('PurchaseDocumentsComponent', () => {

  const url = 'http://localhost:8080/api/purchase-documents';

  let fixture: ComponentFixture<PurchaseDocumentsComponent>;
  let component: PurchaseDocumentsComponent;
  let httpMock: HttpTestingController;
  let router: Router;
  let permissions: string[];

  const draft: PurchaseDocumentSummary = {
    id: 1, type: 'PURCHASE_ORDER', status: 'DRAFT', reference: null, supplierId: 3, supplierName: 'Fournisseur Alpha',
    issueDate: '2026-09-20', total: 37.057, createdAt: '2026-09-20T10:00:00'
  };
  const validated: PurchaseDocumentSummary = { ...draft, id: 2, status: 'VALIDATED', reference: 'PO-2026-00001' };

  const everything = ['SALE_UPDATE', 'SALE_CREATE', 'SALE_CANCEL', 'PURCHASE_UPDATE', 'PURCHASE_CREATE', 'PURCHASE_CANCEL'];

  function open(type: PurchaseDocumentType = 'PURCHASE_ORDER') {
    permissions = everything;
    TestBed.configureTestingModule({
      imports: [PurchaseDocumentsComponent],
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
    fixture = TestBed.createComponent(PurchaseDocumentsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();
  }

  function answer(content: PurchaseDocumentSummary[]) {
    httpMock.expectOne(r => r.url === url).flush({
      content, totalElements: content.length, totalPages: 1, size: 10, number: 0
    });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('asks for the documents of its own type', () => {
    open('GOODS_RECEIPT');

    const req = httpMock.expectOne(r => r.url === url);
    expect(req.request.params.get('type')).toBe('GOODS_RECEIPT');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('shows the empty state when there is nothing yet', () => {
    open();
    answer([]);

    expect(fixture.nativeElement.querySelector('.empty-title').textContent).toContain('No purchase orders yet');
  });

  it('lists the documents, showing a draft as not numbered yet', () => {
    open();
    answer([draft, validated]);

    const rows = fixture.nativeElement.querySelectorAll('.document-row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Not numbered yet');
    expect(rows[1].textContent).toContain('PO-2026-00001');
  });

  it('sends the status filter and goes back to the first page', () => {
    open();
    answer([]);

    component.statusFilter = 'VALIDATED';
    component.onFilterChange();

    const req = httpMock.expectOne(r => r.url === url);
    expect(req.request.params.get('status')).toBe('VALIDATED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  it('opens the editor of a document, and a blank one to create', () => {
    open();
    answer([draft]);

    component.open(draft);
    component.openCreate();

    expect(router.navigate).toHaveBeenCalledWith(['/purchase-orders', 1]);
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-orders', 'new']);
  });

  it('offers to delete a draft only', () => {
    open();
    answer([draft, validated]);

    component.buildRowMenu(draft);
    expect(component.rowMenuItems.map(item => item.label)).toContain('Delete draft');

    component.buildRowMenu(validated);
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
    answer([draft, validated]);
    const opened = spyOn(window, 'open').and.returnValue(null);

    component.buildRowMenu(draft);
    expect(component.rowMenuItems.map(item => item.label)).toContain('Print');
    component.rowMenuItems.find(item => item.label === 'Print')!.command!({} as never);

    expect(opened).toHaveBeenCalledWith('/print/purchases/1', '_blank');
  });

  // ----- Steps of the document's life, from the row menu -----

  function labels(): (string | undefined)[] {
    return component.rowMenuItems.filter(item => !item.separator).map(item => item.label);
  }

  const receipt: PurchaseDocumentSummary = { ...validated, id: 3, type: 'GOODS_RECEIPT', reference: 'GR-2026-00001' };

  it('offers to validate a draft, next to deleting it', () => {
    open();
    answer([draft]);

    component.buildRowMenu(draft);

    expect(labels()).toEqual(['Open', 'Print', 'Validate', 'Delete draft']);
  });

  it('offers to create a receipt and to cancel a validated order, and only to cancel a validated receipt', () => {
    open();
    answer([validated]);

    component.buildRowMenu(validated);
    expect(labels()).toEqual(['Open', 'Print', 'Create receipt', 'Create invoice', 'Cancel order']);

    component.buildRowMenu(receipt);
    expect(labels()).toEqual(['Open', 'Print', 'Create invoice', 'Create return note', 'Cancel receipt']);

    component.buildRowMenu({ ...receipt, status: 'CANCELLED' });
    expect(labels()).toEqual(['Open', 'Print']);
  });

  it('shows only the steps the user is allowed to take', () => {
    open();
    answer([validated]);
    permissions = ['PURCHASE_CREATE'];

    component.buildRowMenu(validated);

    expect(labels()).toEqual(['Open', 'Print', 'Create receipt', 'Create invoice']);
  });

  it('tells the user that validating a receipt puts its goods into the stock, and cancelling takes them out', () => {
    open();
    answer([draft]);
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.buildRowMenu({ ...receipt, status: 'DRAFT', reference: null });
    component.rowMenuItems.find(item => item.label === 'Validate')!.command!({} as never);
    expect(confirm.calls.mostRecent().args[0].message).toContain('added to the stock');

    component.buildRowMenu(receipt);
    component.rowMenuItems.find(item => item.label === 'Cancel receipt')!.command!({} as never);
    expect(confirm.calls.mostRecent().args[0].message).toContain('taken back out of the stock');
    expect(confirm.calls.mostRecent().args[0].acceptButtonStyleClass).toContain('p-button-danger');
  });

  it('asks before any step, and changes nothing when the user declines', () => {
    open();
    answer([validated]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.buildRowMenu(validated);
    component.rowMenuItems.find(item => item.label === 'Cancel order')!.command!({} as never);

    httpMock.expectNone(`${url}/2/cancel`);
  });

  it('validates a draft from the list without opening it, then reloads', () => {
    open();
    answer([draft]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(draft);
    component.rowMenuItems.find(item => item.label === 'Validate')!.command!({} as never);

    httpMock.expectOne(`${url}/1/validate`).flush({ ...draft, status: 'VALIDATED', reference: 'PO-2026-00002' });
    httpMock.expectOne(r => r.url === url).flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('creates a receipt from a validated order and opens it', () => {
    open();
    answer([validated]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(validated);
    component.rowMenuItems.find(item => item.label === 'Create receipt')!.command!({} as never);

    httpMock.expectOne(`${url}/2/convert-to-receipt`).flush({ ...receipt, id: 9 });
    expect(router.navigate).toHaveBeenCalledWith(['/goods-receipts', 9]);
  });

  // ----- Invoices -----

  const invoice: PurchaseDocumentSummary = {
    ...validated, id: 6, type: 'PURCHASE_INVOICE', reference: 'PINV-2026-00001', total: 90, paidAmount: 0, balance: 90
  };

  it('creates an invoice from a validated receipt and opens it', () => {
    open('GOODS_RECEIPT');
    answer([receipt]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(receipt);
    component.rowMenuItems.find(item => item.label === 'Create invoice')!.command!({} as never);

    httpMock.expectOne(`${url}/${receipt.id}/convert-to-invoice`).flush({ ...invoice, id: 21 });
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-invoices', 21]);
  });

  it('creates a credit note from an invoice and opens it', () => {
    open('PURCHASE_INVOICE');
    answer([invoice]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(invoice);
    component.rowMenuItems.find(item => item.label === 'Create credit note')!.command!({} as never);

    httpMock.expectOne(`${url}/6/convert-to-credit-note`).flush({ ...invoice, id: 31, type: 'PURCHASE_CREDIT_NOTE' });
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-credit-notes', 31]);
  });

  it('has no "New" button on the supplier credit notes: they are made from an invoice', () => {
    open('PURCHASE_CREDIT_NOTE');
    answer([]);

    const page = (fixture.nativeElement as HTMLElement).textContent!;
    expect(page).not.toContain('New supplier credit note');
    expect(page).toContain('made from a validated invoice');
  });

  it('shows a validated credit note as applied, with the actions to open, print and cancel it', () => {
    open('PURCHASE_CREDIT_NOTE');
    const note: PurchaseDocumentSummary = { ...invoice, id: 7, type: 'PURCHASE_CREDIT_NOTE', reference: 'PCN-2026-00001' };
    answer([note]);

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Applied');
    component.buildRowMenu(note);
    expect(labels()).toEqual(['Open', 'Print', 'Cancel credit note']);
  });

  it('shows the balance of an invoice, and its status as unpaid, partly paid or paid', () => {
    open('PURCHASE_INVOICE');
    answer([invoice, { ...invoice, id: 7, status: 'PARTIALLY_PAID', paidAmount: 20, balance: 70 }, { ...invoice, id: 8, status: 'PAID', paidAmount: 90, balance: 0 }]);

    const text = (fixture.nativeElement as HTMLElement).textContent!;
    expect(text).toContain('Balance');
    expect(text).toContain('90.000');
    expect(text).toContain('Unpaid');
    expect(text).toContain('Partially paid');
    expect(component.statusOptions.map(option => option.label)).toEqual(['Draft', 'Unpaid', 'Partially paid', 'Paid', 'Cancelled']);
  });

  it('offers "Pay supplier" on an unpaid or partly paid invoice, and only to someone who can update purchases', () => {
    open('PURCHASE_INVOICE');
    answer([invoice]);

    component.buildRowMenu(invoice);
    expect(labels()).toEqual(['Open', 'Print', 'Pay supplier', 'Create credit note', 'Create return note', 'Cancel invoice']);

    component.buildRowMenu({ ...invoice, status: 'PARTIALLY_PAID', paidAmount: 20, balance: 70 });
    expect(labels()).toEqual(['Open', 'Print', 'Pay supplier', 'Create credit note', 'Create return note']);

    component.buildRowMenu({ ...invoice, status: 'PAID', paidAmount: 90, balance: 0 });
    expect(labels()).toEqual(['Open', 'Print', 'Create credit note', 'Create return note']);

    permissions = ['PURCHASE_CANCEL'];
    component.buildRowMenu(invoice);
    expect(labels()).toEqual(['Open', 'Print', 'Cancel invoice']);
  });

  it('opens the payment dialog on the balance of the invoice, and reloads the list once a payment is recorded', () => {
    open('PURCHASE_INVOICE');
    answer([invoice]);
    const dialog = spyOn(component.paymentForm!, 'open');

    component.buildRowMenu({ ...invoice, status: 'PARTIALLY_PAID', paidAmount: 20, balance: 70 });
    component.rowMenuItems.find(item => item.label === 'Pay supplier')!.command!({} as never);

    expect(dialog).toHaveBeenCalledWith({ id: 6, reference: 'PINV-2026-00001', balance: 70 });

    component.paymentForm!.saved.emit();
    httpMock.expectOne(r => r.url === url).flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });

  // ----- Return notes -----

  it('creates a return note from a validated receipt and opens it', () => {
    open('GOODS_RECEIPT');
    answer([receipt]);
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.buildRowMenu(receipt);
    component.rowMenuItems.find(item => item.label === 'Create return note')!.command!({} as never);

    httpMock.expectOne(`${url}/${receipt.id}/convert-to-return-note`)
      .flush({ ...receipt, id: 41, type: 'PURCHASE_RETURN_NOTE', status: 'DRAFT' });
    expect(router.navigate).toHaveBeenCalledWith(['/purchase-return-notes', 41]);
  });

  it('can start a return note by hand, and shows a validated one as sent back, with the actions to open, print and cancel it', () => {
    open('PURCHASE_RETURN_NOTE');
    const note: PurchaseDocumentSummary = { ...validated, id: 7, type: 'PURCHASE_RETURN_NOTE', reference: 'PRN-2026-00001' };
    answer([note]);

    const page = (fixture.nativeElement as HTMLElement).textContent!;
    expect(page).toContain('New supplier return note');
    expect(page).toContain('Sent back');
    component.buildRowMenu(note);
    expect(labels()).toEqual(['Open', 'Print', 'Cancel return note']);
  });
});
