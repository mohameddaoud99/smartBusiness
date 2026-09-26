import { Component, ViewChild, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MenuModule } from 'primeng/menu';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { openPrintPage } from '../print/print-link';
import { PaymentFormComponent } from '../payments/payment-form/payment-form.component';
import { AuthService } from '../../core/auth/auth.service';
import { confirmDocumentAction } from '../../shared/document-action';
import { SalesAction, salesActionOpens, salesActionResult, salesActions, runSalesAction } from './sales-document-actions';
import { SalesDocumentService } from '../../core/services/sales-document.service';
import { NotificationService } from '../../core/services/notification.service';
import {
  DOCUMENT_CONFIGS, SalesDocumentStatus, SalesDocumentSummary, SalesDocumentType,
  canBeCreatedByHand, statusLabel, statusOptions, statusSeverity
} from './sales-document.model';

/**
 * The list of quotes or of sales orders — one component, the route says which
 * (`data.documentType`). Opening a row, or creating, goes to the editor page.
 */
@Component({
  selector: 'app-sales-documents',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, FormsModule, TableModule, ButtonModule, InputTextModule,
    IconFieldModule, InputIconModule, SelectModule, TagModule, MenuModule,
    TooltipModule, PageHeaderComponent, HasPermissionDirective, PaymentFormComponent
  ],
  templateUrl: './sales-documents.component.html',
  styleUrl: './sales-documents.component.scss'
})
export class SalesDocumentsComponent {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly documentService = inject(SalesDocumentService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly auth = inject(AuthService);

  readonly type: SalesDocumentType = this.route.snapshot.data['documentType'];
  readonly config = DOCUMENT_CONFIGS[this.type];
  readonly statusOptions = statusOptions(this.type);
  /** A credit note is made from an invoice: its list has no "New" button. */
  readonly canCreate = canBeCreatedByHand(this.type);
  readonly statusLabel = statusLabel;
  readonly statusSeverity = statusSeverity;

  readonly documents = signal<SalesDocumentSummary[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  searchTerm = '';
  statusFilter: SalesDocumentStatus | null = null;

  rowMenuItems: MenuItem[] = [];

  /** Only in the list of invoices: the dialog behind "Add payment". */
  @ViewChild(PaymentFormComponent) paymentForm?: PaymentFormComponent;

  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10 };
  private readonly searchInput = new Subject<string>();

  constructor() {
    this.searchInput
      .pipe(debounceTime(350), distinctUntilChanged())
      .subscribe(() => this.reload());
  }

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 10;
    this.documentService.search({
      type: this.type,
      search: this.searchTerm,
      status: this.statusFilter,
      page: (event.first ?? 0) / rows,
      size: rows,
      sortField: event.sortField as string,
      sortOrder: event.sortOrder ?? 1
    }).subscribe({
      next: page => {
        this.documents.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSearchChange() {
    this.searchInput.next(this.searchTerm);
  }

  onFilterChange() {
    this.reload();
  }

  clearFilters() {
    this.searchTerm = '';
    this.statusFilter = null;
    this.reload();
  }

  get hasFilters(): boolean {
    return !!this.searchTerm || !!this.statusFilter;
  }

  /** Back to page 1 — results change, so the current offset is meaningless. */
  reload() {
    this.load({ ...this.lastEvent, first: 0 });
  }

  refresh() {
    this.load(this.lastEvent);
  }

  openCreate() {
    this.router.navigate([this.config.path, 'new']);
  }

  open(document: SalesDocumentSummary) {
    this.router.navigate([this.config.path, document.id]);
  }

  buildRowMenu(document: SalesDocumentSummary) {
    const items: MenuItem[] = [
      { label: 'Open', icon: 'pi pi-arrow-right', command: () => this.open(document) },
      { label: 'Print', icon: 'pi pi-print', command: () => openPrintPage(this.router, 'sales', document.id) }
    ];

    // The steps of the document's life, the same as on its own page; each one asks first
    const steps = salesActions(document).filter(step => this.auth.has(step.permission));
    const canPay = this.canPay(document);
    if (steps.length || canPay) {
      items.push({ separator: true });
      if (canPay) {
        items.push({
          label: 'Add payment',
          icon: 'pi pi-wallet',
          command: () => this.paymentForm?.open({
            id: document.id, reference: document.reference ?? '', balance: document.balance ?? document.total
          })
        });
      }
      for (const step of steps) {
        items.push({
          label: step.label,
          icon: step.icon,
          styleClass: step.confirm.danger ? 'menu-danger' : undefined,
          command: () => this.confirmStep(step, document)
        });
      }
    }

    // Only a draft can go — a document that has been numbered is cancelled instead
    if (document.status === 'DRAFT' && this.auth.has('SALE_UPDATE')) {
      items.push(
        { separator: true },
        { label: 'Delete draft', icon: 'pi pi-trash', styleClass: 'menu-danger', command: () => this.confirmDelete(document) }
      );
    }
    this.rowMenuItems = items;
  }

  /** An unpaid or partly paid invoice takes payments — recording one needs the right to update sales. */
  private canPay(document: SalesDocumentSummary): boolean {
    return document.type === 'INVOICE'
      && (document.status === 'ISSUED' || document.status === 'PARTIALLY_PAID')
      && this.auth.has('SALE_UPDATE');
  }

  private confirmStep(step: SalesAction, document: SalesDocumentSummary) {
    confirmDocumentAction(this.confirmation, step, () => {
      runSalesAction(this.documentService, document.id, step.id).subscribe(result => {
        this.notification.success(salesActionResult(step.id, result));
        const opens = salesActionOpens(step.id);
        if (opens) {
          this.router.navigate([DOCUMENT_CONFIGS[opens].path, result.id]);
        } else {
          this.refresh();
        }
      });
    });
  }

  confirmDelete(document: SalesDocumentSummary) {
    this.confirmation.confirm({
      header: `Delete ${this.config.label.toLowerCase()} draft`,
      message: `This draft for ${document.customerName} will be permanently removed. This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.documentService.delete(document.id).subscribe(() => {
          this.notification.success('The draft has been deleted.');
          this.refresh();
        });
      }
    });
  }
}
