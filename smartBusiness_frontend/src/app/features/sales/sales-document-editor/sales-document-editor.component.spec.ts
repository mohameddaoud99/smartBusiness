import { TestBed, ComponentFixture, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';

import { SalesDocumentEditorComponent } from './sales-document-editor.component';
import { SalesDocumentResponse, SalesDocumentType } from '../sales-document.model';
import { TaxResponse } from '../../settings/taxes/tax.model';
import { ProductResponse } from '../../products/product.model';

describe('SalesDocumentEditorComponent', () => {

  const api = 'http://localhost:8080/api';
  const url = `${api}/sales-documents`;

  let fixture: ComponentFixture<SalesDocumentEditorComponent>;
  let component: SalesDocumentEditorComponent;
  let httpMock: HttpTestingController;
  let router: Router;

  function tax(id: number, name: string, kind: TaxResponse['kind'], extra: Partial<TaxResponse> = {}): TaxResponse {
    return {
      id, name, kind, includedInVatBase: false, appliesToLine: kind !== 'FIXED_PER_DOCUMENT',
      activeByDefault: false, active: true, system: true, createdAt: '', updatedAt: '', ...extra
    };
  }

  const taxes: TaxResponse[] = [
    tax(1, 'TVA 19%', 'VAT_RATE', { rate: 19, activeByDefault: true }),
    tax(2, 'TVA 7%', 'VAT_RATE', { rate: 7, active: false }),
    tax(3, 'FODEC', 'PERCENTAGE_SURCHARGE', { rate: 1, includedInVatBase: true, activeByDefault: true }),
    tax(4, 'Timbre fiscal', 'FIXED_PER_DOCUMENT', { amount: 1, activeByDefault: true })
  ];

  const cable = {
    id: 20, reference: 'P-0020', name: 'USB-C Cable', purpose: 'SALE', salePrice: 12.5,
    defaultTaxes: [{ id: 1, name: 'TVA 19%' }]
  } as ProductResponse;

  const purchaseOnly = { id: 21, reference: 'P-0021', name: 'Raw material', purpose: 'PURCHASE', defaultTaxes: [] } as unknown as ProductResponse;

  function page<T>(content: T[]) {
    return { content, totalElements: content.length, totalPages: 1, size: 10, number: 0 };
  }

  function savedDocument(overrides: Partial<SalesDocumentResponse> = {}): SalesDocumentResponse {
    return {
      id: 5, type: 'QUOTE', status: 'DRAFT', reference: null, customerId: 3, customerName: 'Client Alpha',
      issueDate: '2026-09-20', dueDate: null, subtotal: 30, total: 37.057, notes: null, terms: null,
      lines: [
        { id: 1, productId: 20, reference: 'P-0020', designation: 'USB-C Cable', quantity: 1, unitPrice: 30,
          discountRate: 0, vatTaxId: 1, vatRate: 19, lineTotal: 30 }
      ],
      taxes: [
        { taxId: 3, kind: 'PERCENTAGE_SURCHARGE', name: 'FODEC', rate: 1, base: 30, amount: 0.3 },
        { taxId: null, kind: 'VAT_RATE', name: 'VAT 19%', rate: 19, base: 30.3, amount: 5.757 },
        { taxId: 4, kind: 'FIXED_PER_DOCUMENT', name: 'Timbre fiscal', amount: 1 }
      ],
      derived: [],
      ...overrides
    };
  }

  const warehouses = [
    { id: 1, name: 'Main', defaultWarehouse: true, active: true },
    { id: 2, name: 'Sfax', defaultWarehouse: false, active: true },
    { id: 3, name: 'Old', defaultWarehouse: false, active: false }
  ];

  /** Builds the page for a route and answers the option requests it makes (warehouses: delivery notes only). */
  function open(options: { type?: SalesDocumentType; id?: string } = {}) {
    TestBed.configureTestingModule({
      imports: [SalesDocumentEditorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        MessageService,
        ConfirmationService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { data: { documentType: options.type ?? 'QUOTE' } },
            paramMap: of(convertToParamMap(options.id ? { id: options.id } : {}))
          }
        }
      ]
    });

    fixture = TestBed.createComponent(SalesDocumentEditorComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);

    fixture.detectChanges();

    httpMock.expectOne(`${api}/settings/taxes`).flush(taxes);
    httpMock.expectOne(r => r.url === `${api}/products`).flush(page([cable, purchaseOnly]));
    httpMock.expectOne(r => r.url === `${api}/customers`)
      .flush(page([{ id: 3, name: 'Client Alpha' }, { id: 4, name: 'Client Beta' }]));
    if (options.type === 'DELIVERY_NOTE' || options.type === 'INVOICE' || options.type === 'RETURN_NOTE') {
      httpMock.expectOne(`${api}/warehouses`).flush(warehouses);
    }
  }

  function openSaved(document: SalesDocumentResponse) {
    open({ type: document.type, id: '5' });
    httpMock.expectOne(`${url}/5`).flush(document);
    fixture.detectChanges();
    // An issued invoice shows its payments, which the page asks for
    if (document.type === 'INVOICE' && document.status !== 'DRAFT') {
      answerPayments([]);
    }
  }

  function answerPayments(content: unknown[]) {
    const req = httpMock.expectOne(r => r.url === `${api}/payments`);
    expect(req.request.params.get('invoiceId')).toBe('5');
    req.flush(page(content));
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  // ----- New document -----

  it('starts a new draft with one line, the default VAT and the default document taxes', () => {
    open();

    expect(component.title()).toBe('New quote');
    expect(component.isNew()).toBeTrue();
    expect(component.lines.length).toBe(1);
    expect(component.lines.at(0).value.vatTaxId).toBe(1);
    expect(component.form.value.taxIds).toEqual([3, 4]);
    expect(component.loading()).toBeFalse();
  });

  it('offers only active VAT rates on the lines and only surcharges and flat charges on the document', () => {
    open();

    expect(component.vatTaxes().map(t => t.name)).toEqual(['TVA 19%']);
    expect(component.documentTaxes().map(t => t.name)).toEqual(['FODEC', 'Timbre fiscal']);
  });

  it('does not offer a purchase-only product', () => {
    open();

    expect(component.sellableProducts().map(p => p.name)).toEqual(['USB-C Cable']);
  });

  it('is titled after the document type of the route', () => {
    open({ type: 'SALES_ORDER' });

    expect(component.title()).toBe('New sales order');
    expect(component.config.path).toBe('/sales-orders');
  });

  it('does not call the API while the form is invalid', () => {
    open();

    component.saveDraft();

    httpMock.expectNone(url);
    expect(component.form.touched).toBeTrue();
  });

  it('fills a line from the picked product: name, code, price and its default VAT', () => {
    open();

    component.onProductPicked(0, 20);

    const line = component.lines.at(0).value;
    expect(line.designation).toBe('USB-C Cable');
    expect(line.reference).toBe('P-0020');
    expect(line.unitPrice).toBe(12.5);
    expect(line.vatTaxId).toBe(1);
  });

  it('keeps what was typed when the product pick is cleared', () => {
    open();
    component.onProductPicked(0, 20);

    component.onProductPicked(0, null);

    const line = component.lines.at(0).value;
    expect(line.productId).toBeNull();
    expect(line.reference).toBeNull();
    expect(line.designation).toBe('USB-C Cable');
  });

  it('adds and removes lines, but always keeps one', () => {
    open();
    component.addLine();
    expect(component.lines.length).toBe(2);

    component.removeLine(1);
    expect(component.lines.length).toBe(1);
  });

  it('POSTs a new draft with ISO dates, the document taxes and the lines, then opens it', () => {
    open();
    component.form.patchValue({ customerId: 3, issueDate: new Date(2026, 8, 20), notes: 'Thanks' });
    component.lines.at(0).patchValue({ designation: 'Pantalon', quantity: 2, unitPrice: 30 });

    component.saveDraft();

    const req = httpMock.expectOne(url);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.type).toBe('QUOTE');
    expect(req.request.body.customerId).toBe(3);
    expect(req.request.body.issueDate).toBe('2026-09-20');
    expect(req.request.body.dueDate).toBeNull();
    expect(req.request.body.taxIds).toEqual([3, 4]);
    expect(req.request.body.lines[0]).toEqual({
      productId: null, reference: null, designation: 'Pantalon',
      quantity: 2, sourceLineId: null, unitPrice: 30, discountRate: 0, vatTaxId: 1
    });
    req.flush(savedDocument());

    expect(router.navigate).toHaveBeenCalledWith(['/quotes', 5], { replaceUrl: true });
    expect(component.saving()).toBeFalse();
  });

  it('releases the saving state on an API error', () => {
    open();
    component.form.patchValue({ customerId: 3 });
    component.lines.at(0).patchValue({ designation: 'Pantalon' });

    component.saveDraft();
    expect(component.saving()).toBeTrue();
    httpMock.expectOne(url).flush({ message: 'boom' }, { status: 422, statusText: 'Unprocessable' });

    expect(component.saving()).toBeFalse();
  });

  it('asks the server for the totals once the form is valid, after a short pause', fakeAsync(() => {
    open();
    component.form.patchValue({ customerId: 3 });
    component.lines.at(0).patchValue({ designation: 'Pantalon', quantity: 1, unitPrice: 30 });

    tick(300);

    const req = httpMock.expectOne(`${url}/preview`);
    expect(req.request.method).toBe('POST');
    req.flush(savedDocument({ id: null }));
    expect(component.totals()?.total).toBe(37.057);
  }));

  it('asks nothing while the form is invalid', fakeAsync(() => {
    open();
    component.lines.at(0).patchValue({ designation: 'Pantalon' });

    tick(300);

    httpMock.expectNone(`${url}/preview`);
  }));

  // ----- Printing -----

  it('opens the print preview of a saved document in a new tab', () => {
    openSaved(savedDocument());
    const opened = spyOn(window, 'open').and.returnValue(null);

    component.print();

    expect(opened).toHaveBeenCalledWith('/print/sales/5', '_blank');
  });

  it('offers no print button while the document is not saved yet', () => {
    open();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Print');
  });

  // ----- Existing draft -----

  it('loads a saved draft into the form and lets it be edited', () => {
    openSaved(savedDocument());

    expect(component.isNew()).toBeFalse();
    expect(component.readOnly()).toBeFalse();
    expect(component.title()).toBe('Quote draft');
    expect(component.form.value.customerId).toBe(3);
    expect(component.form.value.issueDate).toEqual(new Date(2026, 8, 20));
    expect(component.form.value.taxIds).toEqual([3, 4]);
    expect(component.lines.length).toBe(1);
    expect(component.form.enabled).toBeTrue();
    expect(component.totals()?.total).toBe(37.057);
  });

  it('PUTs a saved draft when it is saved again', () => {
    openSaved(savedDocument());
    component.lines.at(0).patchValue({ quantity: 3 });

    component.saveDraft();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.lines[0].quantity).toBe(3);
    req.flush(savedDocument());
  });

  it('saves, then issues, once the user confirms', () => {
    openSaved(savedDocument());
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.confirmIssue();

    httpMock.expectOne(`${url}/5`).flush(savedDocument());
    httpMock.expectOne(`${url}/5/issue`).flush(savedDocument({ status: 'ISSUED', reference: 'QUO-2026-00001' }));
    expect(component.saving()).toBeFalse();
  });

  it('issues nothing before the user confirms', () => {
    openSaved(savedDocument());
    spyOn(TestBed.inject(ConfirmationService), 'confirm');

    component.confirmIssue();

    httpMock.expectNone(`${url}/5/issue`);
  });

  it('deletes a draft once the user confirms, then goes back to the list', () => {
    openSaved(savedDocument());
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.confirmDelete();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    expect(router.navigate).toHaveBeenCalledWith(['/quotes']);
  });

  // ----- Issued documents -----

  it('shows an issued document read-only, with its number', () => {
    openSaved(savedDocument({ status: 'ISSUED', reference: 'QUO-2026-00001' }));

    expect(component.readOnly()).toBeTrue();
    expect(component.title()).toBe('QUO-2026-00001');
    expect(component.form.disabled).toBeTrue();
  });

  it('offers accept, reject, convert and invoice on an issued quote', () => {
    openSaved(savedDocument({ status: 'ISSUED', reference: 'QUO-2026-00001' }));

    expect(component.actions().map(a => a.label)).toEqual(['Accept', 'Reject', 'Convert to order', 'Create invoice']);
  });

  it('does not offer to convert a quote that already has a live order', () => {
    openSaved(savedDocument({
      status: 'ACCEPTED', reference: 'QUO-2026-00001',
      derived: [{
        id: 8, type: 'SALES_ORDER', status: 'DRAFT', customerId: 3, customerName: 'Client Alpha',
        issueDate: '2026-09-20', total: 37.057, createdAt: ''
      }]
    }));

    expect(component.actions().map(a => a.label)).toEqual(['Reject', 'Back to issued']);
  });

  it('offers to convert again once the order was cancelled', () => {
    openSaved(savedDocument({
      status: 'ACCEPTED', reference: 'QUO-2026-00001',
      derived: [{
        id: 8, type: 'SALES_ORDER', status: 'CANCELLED', customerId: 3, customerName: 'Client Alpha',
        issueDate: '2026-09-20', total: 37.057, createdAt: ''
      }]
    }));

    expect(component.actions().map(a => a.label)).toContain('Convert to order');
  });

  it('offers confirm and cancel on an issued sales order, and only cancel once confirmed', () => {
    openSaved(savedDocument({ type: 'SALES_ORDER', status: 'ISSUED', reference: 'SO-2026-00001' }));
    expect(component.actions().map(a => a.label)).toEqual(['Confirm', 'Cancel order']);
  });

  it('offers nothing on a cancelled sales order', () => {
    openSaved(savedDocument({ type: 'SALES_ORDER', status: 'CANCELLED', reference: 'SO-2026-00001' }));
    expect(component.actions()).toEqual([]);
  });

  it('moves a quote to accepted', () => {
    openSaved(savedDocument({ status: 'ISSUED', reference: 'QUO-2026-00001' }));

    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());
    component.actions().find(a => a.label === 'Accept')!.run();

    const req = httpMock.expectOne(`${url}/5/status`);
    expect(req.request.body).toEqual({ status: 'ACCEPTED' });
    req.flush(savedDocument({ status: 'ACCEPTED', reference: 'QUO-2026-00001' }));
    expect(component.document()?.status).toBe('ACCEPTED');
  });

  it('converts a quote and opens the new order', () => {
    openSaved(savedDocument({ status: 'ISSUED', reference: 'QUO-2026-00001' }));

    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());
    component.actions().find(a => a.label === 'Convert to order')!.run();

    httpMock.expectOne(`${url}/5/convert-to-order`)
      .flush(savedDocument({ id: 9, type: 'SALES_ORDER', sourceId: 5 }));
    expect(router.navigate).toHaveBeenCalledWith(['/sales-orders', 9]);
  });

  it('cancels a sales order once the user confirms', () => {
    openSaved(savedDocument({ type: 'SALES_ORDER', status: 'CONFIRMED', reference: 'SO-2026-00001' }));
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Cancel order')!.run();

    const req = httpMock.expectOne(`${url}/5/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush(savedDocument({ type: 'SALES_ORDER', status: 'CANCELLED', reference: 'SO-2026-00001' }));
    expect(component.document()?.status).toBe('CANCELLED');
  });

  // ----- Delivery note -----

  const deliveryNote = (overrides: Partial<SalesDocumentResponse> = {}) =>
    savedDocument({ type: 'DELIVERY_NOTE', warehouseId: 2, warehouseName: 'Sfax', ...overrides });

  it('starts a delivery note on the default warehouse, and offers only the active ones', () => {
    open({ type: 'DELIVERY_NOTE' });

    expect(component.title()).toBe('New delivery note');
    expect(component.warehouses().map(w => w.name)).toEqual(['Main', 'Sfax']);
    expect(component.form.value.warehouseId).toBe(1);
  });

  it('requires a warehouse on a delivery note only', () => {
    open({ type: 'DELIVERY_NOTE' });
    component.form.patchValue({ warehouseId: null });
    expect(component.form.controls['warehouseId'].valid).toBeFalse();
  });

  it('asks for no warehouse on a quote, and sends none', () => {
    open();

    expect(component.form.controls['warehouseId'].valid).toBeTrue();
    httpMock.expectNone(`${api}/warehouses`);
    component.form.patchValue({ customerId: 3, issueDate: new Date(2026, 8, 20) });
    component.lines.at(0).patchValue({ designation: 'Pantalon', quantity: 1, unitPrice: 30 });
    component.saveDraft();

    expect(httpMock.expectOne(url).request.body.warehouseId).toBeNull();
  });

  it('POSTs a delivery note with its warehouse', () => {
    open({ type: 'DELIVERY_NOTE' });
    component.form.patchValue({ customerId: 3, warehouseId: 2, issueDate: new Date(2026, 8, 20) });
    component.lines.at(0).patchValue({ designation: 'Pantalon', quantity: 1, unitPrice: 30 });

    component.saveDraft();

    const req = httpMock.expectOne(url);
    expect(req.request.body.type).toBe('DELIVERY_NOTE');
    expect(req.request.body.warehouseId).toBe(2);
    req.flush(deliveryNote());
    expect(router.navigate).toHaveBeenCalledWith(['/delivery-notes', 5], { replaceUrl: true });
  });

  it('keeps showing a warehouse that was deactivated since the note was saved', () => {
    openSaved(deliveryNote({ warehouseId: 3, warehouseName: 'Old' }));

    expect(component.form.value.warehouseId).toBe(3);
    expect(component.warehouses().some(w => w.id === 3 && w.name === 'Old')).toBeTrue();
  });

  it('offers to deliver an issued delivery note, and only to cancel a delivered one', () => {
    openSaved(deliveryNote({ status: 'ISSUED', reference: 'BL-2026-00001' }));
    expect(component.actions().map(a => a.label)).toEqual(['Mark as delivered', 'Cancel delivery note']);
  });

  it('marks a delivery note as delivered once the user confirms, and says the stock is taken', () => {
    openSaved(deliveryNote({ status: 'ISSUED', reference: 'BL-2026-00001' }));
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Mark as delivered')!.run();

    expect(confirm.calls.mostRecent().args[0].message).toContain('taken out of the stock');
    const req = httpMock.expectOne(`${url}/5/status`);
    expect(req.request.body).toEqual({ status: 'DELIVERED' });
    req.flush(deliveryNote({ status: 'DELIVERED', reference: 'BL-2026-00001' }));
    expect(component.document()?.status).toBe('DELIVERED');
  });

  it('cancels a delivered delivery note once the user confirms', () => {
    openSaved(deliveryNote({ status: 'DELIVERED', reference: 'BL-2026-00001' }));
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Cancel delivery note')!.run();

    const req = httpMock.expectOne(`${url}/5/cancel`);
    req.flush(deliveryNote({ status: 'CANCELLED', reference: 'BL-2026-00001' }));
    expect(component.document()?.status).toBe('CANCELLED');
  });

  it('creates a delivery note from a confirmed order and opens it', () => {
    openSaved(savedDocument({ type: 'SALES_ORDER', status: 'CONFIRMED', reference: 'SO-2026-00001' }));
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Create delivery note')!.run();

    httpMock.expectOne(`${url}/5/convert-to-delivery-note`).flush(deliveryNote({ id: 11, sourceId: 5 }));
    expect(router.navigate).toHaveBeenCalledWith(['/delivery-notes', 11]);
  });

  // ----- Invoice -----

  const invoice = (overrides: Partial<SalesDocumentResponse> = {}) =>
    savedDocument({
      type: 'INVOICE', warehouseId: 2, warehouseName: 'Sfax', reference: 'INV-2026-00001', status: 'ISSUED',
      total: 50, paidAmount: 0, balance: 50, ...overrides
    });

  it('starts a new invoice on the default warehouse, which it requires', () => {
    open({ type: 'INVOICE' });

    expect(component.title()).toBe('New invoice');
    expect(component.form.value.warehouseId).toBe(1);
    component.form.patchValue({ warehouseId: null });
    expect(component.form.controls['warehouseId'].valid).toBeFalse();
  });

  it('POSTs a new invoice with its warehouse, then opens it', () => {
    open({ type: 'INVOICE' });
    component.form.patchValue({ customerId: 3, warehouseId: 2, issueDate: new Date(2026, 8, 20) });
    component.lines.at(0).patchValue({ designation: 'Pantalon', quantity: 1, unitPrice: 30 });

    component.saveDraft();

    const req = httpMock.expectOne(url);
    expect(req.request.body.type).toBe('INVOICE');
    expect(req.request.body.warehouseId).toBe(2);
    req.flush(invoice({ status: 'DRAFT', reference: null }));
    expect(router.navigate).toHaveBeenCalledWith(['/invoices', 5], { replaceUrl: true });
  });

  it('shows the payments of an issued invoice, and none while it is a draft', () => {
    openSaved(invoice({ status: 'DRAFT', reference: null }));
    expect((fixture.nativeElement as HTMLElement).querySelector('app-invoice-payments')).toBeNull();
    httpMock.expectNone(r => r.url === `${api}/payments`);
  });

  it('lists the payments of an issued invoice with what is left to pay', () => {
    openSaved(invoice({ status: 'PARTIALLY_PAID', paidAmount: 20, balance: 30 }));

    const text = (fixture.nativeElement as HTMLElement).querySelector('app-invoice-payments')!.textContent!;
    expect(text).toContain('Payments');
    expect(text).toContain('30.000');
    expect(text).toContain('No payment yet');
  });

  it('titles the status of an unpaid invoice "Unpaid"', () => {
    openSaved(invoice());

    expect((fixture.nativeElement as HTMLElement).querySelector('app-page-header')!.textContent).toContain('Unpaid');
  });

  it('offers a credit note and the cancellation of an unpaid invoice, and only the credit note once it has been paid', () => {
    openSaved(invoice());
    expect(component.actions().map(a => a.label)).toEqual(['Create credit note', 'Create return note', 'Cancel invoice']);

    component.document.set(invoice({ status: 'PARTIALLY_PAID', paidAmount: 20, balance: 30 }));
    expect(component.actions().map(a => a.label)).toEqual(['Create credit note', 'Create return note']);
  });

  it('cancels an unpaid invoice once the user confirms, and says what happens to the stock', () => {
    openSaved(invoice());
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Cancel invoice')!.run();

    expect(confirm.calls.mostRecent().args[0].message).toContain('comes back');
    httpMock.expectOne(`${url}/5/cancel`).flush(invoice({ status: 'CANCELLED' }));
    expect(component.document()?.status).toBe('CANCELLED');
    fixture.detectChanges();
    answerPayments([]); // the panel follows the new document
  });

  it('reloads the invoice, and its payments, once a payment was recorded or cancelled', () => {
    openSaved(invoice());

    component.reloadDocument();
    httpMock.expectOne(`${url}/5`).flush(invoice({ status: 'PAID', paidAmount: 50, balance: 0 }));
    fixture.detectChanges();

    expect(component.document()?.status).toBe('PAID');
    answerPayments([]);
  });

  it('links an invoice to the delivery note it was made from', () => {
    openSaved(invoice({ sourceId: 9, sourceReference: 'BL-2026-00001', sourceType: 'DELIVERY_NOTE' }));

    const link = (fixture.nativeElement as HTMLElement).querySelector('.link-line a') as HTMLAnchorElement;
    expect(link.textContent).toContain('BL-2026-00001');
    expect(link.getAttribute('href')).toBe('/delivery-notes/9');
  });

  // ----- Credit note -----

  const creditNote = (overrides: Partial<SalesDocumentResponse> = {}) =>
    savedDocument({
      type: 'CREDIT_NOTE', reference: 'CN-2026-00001', status: 'ISSUED', sourceId: 9,
      sourceReference: 'INV-2026-00001', sourceType: 'INVOICE', ...overrides
    });

  it('creates a credit note from an invoice and opens it', () => {
    openSaved(invoice());
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Create credit note')!.run();

    httpMock.expectOne(`${url}/5/convert-to-credit-note`).flush(creditNote({ id: 13, status: 'DRAFT', reference: null }));
    expect(router.navigate).toHaveBeenCalledWith(['/credit-notes', 13]);
  });

  it('keeps the customer of the invoice on a draft credit note', () => {
    openSaved(creditNote({ status: 'DRAFT', reference: null }));

    expect(component.form.controls['customerId'].disabled).toBeTrue();
    expect(component.form.controls['taxIds'].enabled).toBeTrue();
    expect(component.form.getRawValue().customerId).toBe(3);
  });

  it('shows an issued credit note, linked to its invoice, with the cancellation as its only step', () => {
    openSaved(creditNote());

    expect(component.actions().map(a => a.label)).toEqual(['Cancel credit note']);
    const link = (fixture.nativeElement as HTMLElement).querySelector('.link-line a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/invoices/9');
    expect((fixture.nativeElement as HTMLElement).querySelector('app-page-header')!.textContent).toContain('Applied');
  });

  it('asks for no warehouse on a credit note: it moves no stock', () => {
    openSaved(creditNote({ status: 'DRAFT', reference: null }));

    expect(component.hasWarehouse).toBeFalse();
    httpMock.expectNone(`${api}/warehouses`);
  });

  it('creates an invoice from a delivered delivery note and opens it', () => {
    openSaved(deliveryNote({ status: 'DELIVERED', reference: 'BL-2026-00001' }));
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Create invoice')!.run();

    httpMock.expectOne(`${url}/5/convert-to-invoice`).flush(invoice({ id: 12, status: 'DRAFT', sourceId: 5 }));
    expect(router.navigate).toHaveBeenCalledWith(['/invoices', 12]);
  });

  it('asks before every change of status, and does nothing when the user declines', () => {
    openSaved(savedDocument({ status: 'ISSUED', reference: 'QUO-2026-00001' }));
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm');

    for (const action of component.actions()) {
      action.run();
    }

    expect(confirm).toHaveBeenCalledTimes(4); // accept, reject, convert, create invoice
    httpMock.expectNone(`${url}/5/status`);
    httpMock.expectNone(`${url}/5/convert-to-order`);
    httpMock.expectNone(`${url}/5/convert-to-invoice`);
  });

  it('confirms a sales order, once the user agrees, and says the goods are reserved', () => {
    openSaved(savedDocument({ type: 'SALES_ORDER', status: 'ISSUED', reference: 'SO-2026-00001' }));
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Confirm')!.run();

    expect(confirm.calls.mostRecent().args[0].message).toContain('reserved');
    const req = httpMock.expectOne(`${url}/5/status`);
    expect(req.request.body).toEqual({ status: 'CONFIRMED' });
    req.flush(savedDocument({ type: 'SALES_ORDER', status: 'CONFIRMED', reference: 'SO-2026-00001' }));
    expect(component.document()?.status).toBe('CONFIRMED');
  });

  // ----- Return note -----

  const returnNote = (overrides: Partial<SalesDocumentResponse> = {}) =>
    savedDocument({
      type: 'RETURN_NOTE', warehouseId: 2, warehouseName: 'Sfax', reference: 'RN-2026-00001', status: 'ISSUED',
      sourceId: 9, sourceReference: 'BL-2026-00001', sourceType: 'DELIVERY_NOTE', ...overrides
    });

  it('starts a new return note on the default warehouse, which it requires', () => {
    open({ type: 'RETURN_NOTE' });

    expect(component.title()).toBe('New return note');
    expect(component.form.value.warehouseId).toBe(1);
    component.form.patchValue({ warehouseId: null });
    expect(component.form.controls['warehouseId'].valid).toBeFalse();
  });

  it('creates a return note from a delivered delivery note and opens it', () => {
    openSaved(deliveryNote({ status: 'DELIVERED', reference: 'BL-2026-00001' }));
    spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Create return note')!.run();

    httpMock.expectOne(`${url}/5/convert-to-return-note`).flush(returnNote({ id: 14, status: 'DRAFT', reference: null }));
    expect(router.navigate).toHaveBeenCalledWith(['/return-notes', 14]);
  });

  it('shows an issued return note as received, linked to the note it was made from, with the cancellation as its only step', () => {
    openSaved(returnNote());

    expect(component.actions().map(a => a.label)).toEqual(['Cancel return note']);
    const link = (fixture.nativeElement as HTMLElement).querySelector('.link-line a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/delivery-notes/9');
    expect((fixture.nativeElement as HTMLElement).querySelector('app-page-header')!.textContent).toContain('Received');
  });

  it('cancels a return note once the user confirms, and says the goods go out of the stock again', () => {
    openSaved(returnNote());
    const confirm = spyOn(TestBed.inject(ConfirmationService), 'confirm').and.callFake(options => options.accept?.());

    component.actions().find(a => a.label === 'Cancel return note')!.run();

    expect(confirm.calls.mostRecent().args[0].message).toContain('go back out of the stock');
    httpMock.expectOne(`${url}/5/cancel`).flush(returnNote({ status: 'CANCELLED' }));
    expect(component.document()?.status).toBe('CANCELLED');
  });

  // ----- Quantities followed line by line -----

  const followedLine = { id: 1, productId: 20, reference: 'P-0020', designation: 'USB-C Cable', quantity: 6, unitPrice: 30,
    discountRate: 0, vatTaxId: 1, vatRate: 19, lineTotal: 180, sourceLineId: 7, sourceRemaining: 6 };

  it('says what a draft may still take of its source line, and refuses more', () => {
    openSaved(deliveryNote({ lines: [followedLine] }));

    expect((fixture.nativeElement as HTMLElement).querySelector('.qty-hint')!.textContent).toContain('6 left to deliver');
    component.lines.at(0).patchValue({ quantity: 7 });
    expect(component.lines.at(0).controls['quantity'].valid).toBeFalse();
    component.lines.at(0).patchValue({ quantity: 6 });
    expect(component.lines.at(0).controls['quantity'].valid).toBeTrue();
  });

  it('sends the link to the source line back with the draft', () => {
    openSaved(deliveryNote({ lines: [followedLine] }));

    component.saveDraft();

    const req = httpMock.expectOne(`${url}/5`);
    expect(req.request.body.lines[0].sourceLineId).toBe(7);
    req.flush(deliveryNote({ lines: [followedLine] }));
  });

  it('shows on a saved order what has been delivered of each line, and what is left', () => {
    openSaved(savedDocument({
      type: 'SALES_ORDER', status: 'CONFIRMED', reference: 'SO-2026-00001',
      lines: [{ ...followedLine, quantity: 10, sourceLineId: null, sourceRemaining: null, fulfilledQuantity: 4, remainingQuantity: 6 }]
    }));

    expect((fixture.nativeElement as HTMLElement).querySelector('.qty-hint')!.textContent).toContain('delivered 4 · 6 left');
  });

  it('shows no hint on a line that follows nothing', () => {
    openSaved(savedDocument());

    expect((fixture.nativeElement as HTMLElement).querySelector('.qty-hint')).toBeNull();
    component.lines.at(0).patchValue({ quantity: 1000 });
    expect(component.lines.at(0).controls['quantity'].valid).toBeTrue();
  });
});
