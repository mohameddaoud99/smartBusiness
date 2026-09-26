import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MessageService } from 'primeng/api';
import { PdfExportService } from './pdf-export.service';
import { NotificationService } from '../../core/services/notification.service';

import { DocumentPrintComponent, PrintFamily } from './document-print.component';
import { SalesDocumentResponse } from '../sales/sales-document.model';
import { PurchaseDocumentResponse } from '../purchases/purchase-document.model';

describe('DocumentPrintComponent', () => {

  const api = 'http://localhost:8080/api';

  let fixture: ComponentFixture<DocumentPrintComponent>;
  let component: DocumentPrintComponent;
  let httpMock: HttpTestingController;
  let printed: number;
  let closed: number;

  const profile = {
    name: 'ABC Distribution', currency: 'TND', taxId: '1234567A/A/M/000',
    logoDataUri: null, stampDataUri: null, bankAccounts: []
  };

  const quote: SalesDocumentResponse = {
    id: 5, type: 'QUOTE', status: 'ISSUED', reference: 'QUO-2026-00002', customerId: 3, customerName: 'Client Alpha',
    issueDate: '2026-09-20', dueDate: null, subtotal: 30, total: 37.057, notes: null, terms: null,
    lines: [{ designation: 'pantalon gucci', reference: 'pg 001', quantity: 1, unitPrice: 30, discountRate: 0, vatRate: 19, lineTotal: 30 }],
    taxes: [{ kind: 'VAT_RATE', name: 'VAT 19%', rate: 19, base: 30.3, amount: 5.757 }],
    derived: []
  };

  const receipt: PurchaseDocumentResponse = {
    id: 8, type: 'GOODS_RECEIPT', status: 'VALIDATED', reference: 'GR-2026-00001', supplierId: 4, supplierName: 'Fournisseur Beta',
    warehouseId: 1, warehouseName: 'Entrepôt Sfax', issueDate: '2026-09-21', dueDate: null, subtotal: 10, total: 10,
    notes: null, terms: null,
    lines: [{ designation: 'Tissu', quantity: 2, unitPrice: 5, discountRate: 0, vatRate: 0, lineTotal: 10 }],
    taxes: [], derived: []
  };

  function open(family: PrintFamily, id: number) {
    // The browser's real print dialog and tab closing have no place in a test
    printed = 0;
    closed = 0;
    spyOn(window, 'print').and.callFake(() => { printed++; });
    spyOn(window, 'close').and.callFake(() => { closed++; });
    TestBed.configureTestingModule({
      imports: [DocumentPrintComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        MessageService,
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data: { family }, paramMap: convertToParamMap({ id: String(id) }) } }
        }
      ]
    });
    fixture = TestBed.createComponent(DocumentPrintComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('loads a sales document with its customer and the company profile, and shows the sheet', () => {
    open('sales', 5);

    httpMock.expectOne(`${api}/sales-documents/5`).flush(quote);
    httpMock.expectOne(`${api}/print-profile`).flush(profile);
    httpMock.expectOne(`${api}/customers/3`).flush({
      id: 3, name: 'Client Alpha', taxId: '7654321B/A/M/000', billingAddress: { city: 'Sfax' }
    });
    fixture.detectChanges();

    const sheet = fixture.nativeElement as HTMLElement;
    expect(sheet.querySelector('.doc-title')!.textContent).toContain('DEVIS');
    expect(sheet.querySelector('.party-name')!.textContent).toContain('Client Alpha');
    expect(sheet.querySelector('.in-words')!.textContent).toContain('trente-sept dinars et cinquante-sept millimes');
  });

  it('loads a purchase document with its supplier, and words it as a goods receipt', () => {
    open('purchases', 8);

    httpMock.expectOne(`${api}/purchase-documents/8`).flush(receipt);
    httpMock.expectOne(`${api}/print-profile`).flush(profile);
    httpMock.expectOne(`${api}/suppliers/4`).flush({ id: 4, name: 'Fournisseur Beta' });
    fixture.detectChanges();

    const sheet = fixture.nativeElement as HTMLElement;
    expect(sheet.querySelector('.doc-title')!.textContent).toContain('BON DE RÉCEPTION');
    expect(sheet.querySelector('.party-label')!.textContent).toContain('Fournisseur');
    expect(sheet.textContent).toContain('Entrepôt Sfax');
  });

  it('names the browser tab, and so the PDF, after the document number', () => {
    open('sales', 5);
    httpMock.expectOne(`${api}/sales-documents/5`).flush(quote);
    httpMock.expectOne(`${api}/print-profile`).flush(profile);
    httpMock.expectOne(`${api}/customers/3`).flush({ id: 3, name: 'Client Alpha' });

    expect(TestBed.inject(Title).getTitle()).toBe('QUO-2026-00002 - DEVIS');
  });

  it('shows a message, not a blank page, when the document cannot be loaded', () => {
    open('sales', 99);

    httpMock.expectOne(`${api}/sales-documents/99`).flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });
    // the profile request is abandoned as soon as the document one fails
    expect(httpMock.expectOne(`${api}/print-profile`).cancelled).toBeTrue();
    fixture.detectChanges();

    expect(component.failed()).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('could not be loaded');
  });

  it('hides the prices and the reference on request', () => {
    open('sales', 5);
    httpMock.expectOne(`${api}/sales-documents/5`).flush(quote);
    httpMock.expectOne(`${api}/print-profile`).flush(profile);
    httpMock.expectOne(`${api}/customers/3`).flush({ id: 3, name: 'Client Alpha' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.totals')).not.toBeNull();

    component.setOption('showPrices', false);
    component.setOption('showReference', false);
    fixture.detectChanges();

    const sheet = fixture.nativeElement as HTMLElement;
    expect(sheet.querySelector('.totals')).toBeNull();
    expect(sheet.querySelector('.lines thead')!.textContent).not.toContain('Réf.');
  });

  it('opens the print dialog and closes the tab through the browser window', () => {
    open('sales', 5);
    httpMock.expectOne(`${api}/sales-documents/5`).flush(quote);
    httpMock.expectOne(`${api}/print-profile`).flush(profile);
    httpMock.expectOne(`${api}/customers/3`).flush({ id: 3, name: 'Client Alpha' });

    component.print();
    component.close();

    expect(printed).toBe(1);
    expect(closed).toBe(1);
  });

  // ----- Download -----

  function openLoadedQuote() {
    open('sales', 5);
    httpMock.expectOne(`${api}/sales-documents/5`).flush(quote);
    httpMock.expectOne(`${api}/print-profile`).flush(profile);
    httpMock.expectOne(`${api}/customers/3`).flush({ id: 3, name: 'Client Alpha' });
    fixture.detectChanges();
  }

  it('downloads the sheet as a PDF named after the document, laid out for a page', async () => {
    openLoadedQuote();
    const download = spyOn(TestBed.inject(PdfExportService), 'download').and.resolveTo();

    await component.download();

    expect(download).toHaveBeenCalledTimes(1);
    const [element, filename] = download.calls.mostRecent().args;
    expect(filename).toBe('QUO-2026-00002 - DEVIS.pdf');
    expect(element.classList).toContain('pdf-mode');
    expect(element.textContent).toContain('trente-sept dinars');
    expect(component.downloading()).toBeFalse();
  });

  it('downloads what the user chose to show: no prices, no amounts in the file', async () => {
    openLoadedQuote();
    component.setOption('showPrices', false);
    fixture.detectChanges();
    const download = spyOn(TestBed.inject(PdfExportService), 'download').and.resolveTo();

    await component.download();

    expect(download.calls.mostRecent().args[0].querySelector('.totals')).toBeNull();
  });

  it('shows the button as busy while the file is being built', async () => {
    openLoadedQuote();
    let release!: () => void;
    spyOn(TestBed.inject(PdfExportService), 'download')
      .and.returnValue(new Promise<void>(resolve => release = resolve));

    const running = component.download();
    expect(component.downloading()).toBeTrue();
    release();
    await running;

    expect(component.downloading()).toBeFalse();
  });

  it('tells the user when the PDF cannot be built, and frees the button', async () => {
    openLoadedQuote();
    spyOn(TestBed.inject(PdfExportService), 'download').and.rejectWith(new Error('canvas'));
    const error = spyOn(TestBed.inject(NotificationService), 'error');

    await component.download();

    expect(error).toHaveBeenCalled();
    expect(component.downloading()).toBeFalse();
  });

  it('a draft is downloaded as "Brouillon - DEVIS", never with a number it does not have', async () => {
    open('sales', 6);
    httpMock.expectOne(`${api}/sales-documents/6`).flush({ ...quote, id: 6, status: 'DRAFT', reference: null });
    httpMock.expectOne(`${api}/print-profile`).flush(profile);
    httpMock.expectOne(`${api}/customers/3`).flush({ id: 3, name: 'Client Alpha' });
    fixture.detectChanges();
    const download = spyOn(TestBed.inject(PdfExportService), 'download').and.resolveTo();

    await component.download();

    expect(download.calls.mostRecent().args[1]).toBe('Brouillon - DEVIS.pdf');
  });
});
