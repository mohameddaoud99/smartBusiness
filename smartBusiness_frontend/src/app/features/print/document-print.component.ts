import { Component, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { Observable, forkJoin, map, switchMap } from 'rxjs';

import { SalesDocumentService } from '../../core/services/sales-document.service';
import { PurchaseDocumentService } from '../../core/services/purchase-document.service';
import { CustomerService } from '../../core/services/customer.service';
import { SupplierService } from '../../core/services/supplier.service';
import { PrintProfileService } from '../../core/services/print-profile.service';
import { NotificationService } from '../../core/services/notification.service';
import { PdfExportService } from './pdf-export.service';
import { PrintableDocumentComponent } from './printable-document/printable-document.component';
import {
  PrintOptions, PrintProfile, PrintableDocument, fromPurchaseDocument, fromSalesDocument
} from './printable-document.model';

/** Which family of documents a print route serves — the route says so (`data.family`). */
export type PrintFamily = 'sales' | 'purchases';

/**
 * The print preview: a page of its own, outside the application layout, that shows one document
 * as it will come out of the printer. "Print / Save as PDF" opens the browser's print dialog —
 * which is also how a PDF is made, so there is one path for both. The toolbar is hidden on paper.
 */
@Component({
  selector: 'app-document-print',
  standalone: true,
  imports: [FormsModule, ButtonModule, CheckboxModule, PrintableDocumentComponent],
  templateUrl: './document-print.component.html',
  styleUrl: './document-print.component.scss'
})
export class DocumentPrintComponent {

  private readonly route = inject(ActivatedRoute);
  private readonly page = inject(DOCUMENT);
  private readonly title = inject(Title);
  private readonly salesService = inject(SalesDocumentService);
  private readonly purchaseService = inject(PurchaseDocumentService);
  private readonly customerService = inject(CustomerService);
  private readonly supplierService = inject(SupplierService);
  private readonly profileService = inject(PrintProfileService);
  private readonly pdfExport = inject(PdfExportService);
  private readonly notification = inject(NotificationService);

  readonly document = signal<PrintableDocument | null>(null);
  readonly company = signal<PrintProfile | null>(null);
  readonly failed = signal(false);
  readonly downloading = signal(false);
  readonly options = signal<PrintOptions>({ showPrices: true, showReference: true });

  constructor() {
    const family: PrintFamily = this.route.snapshot.data['family'];
    const id = Number(this.route.snapshot.paramMap.get('id'));

    forkJoin({
      document: family === 'sales' ? this.loadSales(id) : this.loadPurchase(id),
      company: this.profileService.find()
    }).subscribe({
      next: ({ document, company }) => {
        this.document.set(document);
        this.company.set(company);
        // The browser proposes the page title as the name of the PDF: make it the document number
        this.title.setTitle(`${document.reference ?? 'Brouillon'} - ${document.title}`);
      },
      error: () => this.failed.set(true)
    });
  }

  setOption(option: keyof PrintOptions, value: boolean) {
    this.options.update(current => ({ ...current, [option]: value }));
  }

  print() {
    this.page.defaultView?.print();
  }

  /**
   * Saves the sheet as a PDF file, without going through the print dialog. What is photographed is
   * a copy of the sheet as it is shown now — with the options the user chose — laid out for a page.
   */
  async download() {
    const sheet = this.page.querySelector('.sheet');
    const document = this.document();
    if (!sheet || !document) {
      return;
    }

    const copy = sheet.cloneNode(true) as HTMLElement;
    copy.classList.add('pdf-mode');

    this.downloading.set(true);
    try {
      await this.pdfExport.download(copy, `${fileName(document)}.pdf`);
    } catch {
      this.notification.error('The PDF could not be generated. Try again, or use Print and save it as a PDF from there.');
    } finally {
      this.downloading.set(false);
    }
  }

  close() {
    this.page.defaultView?.close();
  }

  private loadSales(id: number): Observable<PrintableDocument> {
    return this.salesService.findById(id).pipe(
      switchMap(document => this.customerService.findById(document.customerId!)
        .pipe(map(customer => fromSalesDocument(document, customer)))));
  }

  private loadPurchase(id: number): Observable<PrintableDocument> {
    return this.purchaseService.findById(id).pipe(
      switchMap(document => this.supplierService.findById(document.supplierId!)
        .pipe(map(supplier => fromPurchaseDocument(document, supplier)))));
  }
}

/** "DEVIS-2026-0002 - DEVIS" — the number first, without anything a file system refuses. */
function fileName(document: PrintableDocument): string {
  return `${document.reference ?? 'Brouillon'} - ${document.title}`.replace(/[\\/:*?"<>|]/g, '-');
}
