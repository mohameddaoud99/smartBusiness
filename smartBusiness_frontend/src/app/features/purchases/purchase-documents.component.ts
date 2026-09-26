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
import {
  PurchaseAction, purchaseActionOpens, purchaseActionResult, purchaseActions, runPurchaseAction
} from './purchase-document-actions';
import { PurchaseDocumentService } from '../../core/services/purchase-document.service';
import { NotificationService } from '../../core/services/notification.service';
import {
  PURCHASE_CONFIGS, PurchaseDocumentStatus, PurchaseDocumentSummary, PurchaseDocumentType,
  canBeCreatedByHand, statusLabel, statusOptions, statusSeverity
} from './purchase-document.model';

/**
 * The list of purchase orders or of goods receipts — one component, the route says which
 * (`data.documentType`). Opening a row, or creating, goes to the editor page.
 */
@Component({
  selector: 'app-purchase-documents',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, FormsModule, TableModule, ButtonModule, InputTextModule,
    IconFieldModule, InputIconModule, SelectModule, TagModule, MenuModule,
    TooltipModule, PageHeaderComponent, HasPermissionDirective, PaymentFormComponent
  ],
  templateUrl: './purchase-documents.component.html',
  styleUrl: './purchase-documents.component.scss'
})
export class PurchaseDocumentsComponent {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly documentService = inject(PurchaseDocumentService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly auth = inject(AuthService);

  readonly type: PurchaseDocumentType = this.route.snapshot.data['documentType'];
  readonly config = PURCHASE_CONFIGS[this.type];
  /** A supplier credit note is made from an invoice: its list has no "New" button. */
  readonly canCreate = canBeCreatedByHand(this.type);
  readonly statusOptions = statusOptions(this.type);
  readonly statusLabel = statusLabel;
  readonly statusSeverity = statusSeverity;

  readonly documents = signal<PurchaseDocumentSummary[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  searchTerm = '';
  statusFilter: PurchaseDocumentStatus | null = null;

  rowMenuItems: MenuItem[] = [];

  /** Only in the list of purchase invoices: the dialog behind "Pay supplier". */
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

  open(document: PurchaseDocumentSummary) {
    this.router.navigate([this.config.path, document.id]);
  }

  buildRowMenu(document: PurchaseDocumentSummary) {
    const items: MenuItem[] = [
      { label: 'Open', icon: 'pi pi-arrow-right', command: () => this.open(document) },
      { label: 'Print', icon: 'pi pi-print', command: () => openPrintPage(this.router, 'purchases', document.id) }
    ];

    // The steps of the document's life, the same as on its own page; each one asks first
    const steps = purchaseActions(document).filter(step => this.auth.has(step.permission));
    const canPay = this.canPay(document);
    if (steps.length || canPay) {
      items.push({ separator: true });
      if (canPay) {
        items.push({
          label: 'Pay supplier',
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
    if (document.status === 'DRAFT' && this.auth.has('PURCHASE_UPDATE')) {
      items.push(
        { separator: true },
        { label: 'Delete draft', icon: 'pi pi-trash', styleClass: 'menu-danger', command: () => this.confirmDelete(document) }
      );
    }
    this.rowMenuItems = items;
  }

  /** An unpaid or partly paid invoice takes payments — paying needs the right to update purchases. */
  private canPay(document: PurchaseDocumentSummary): boolean {
    return document.type === 'PURCHASE_INVOICE'
      && (document.status === 'VALIDATED' || document.status === 'PARTIALLY_PAID')
      && this.auth.has('PURCHASE_UPDATE');
  }

  private confirmStep(step: PurchaseAction, document: PurchaseDocumentSummary) {
    confirmDocumentAction(this.confirmation, step, () => {
      runPurchaseAction(this.documentService, document.id, step.id).subscribe(result => {
        this.notification.success(purchaseActionResult(step.id, result));
        const opens = purchaseActionOpens(step.id);
        if (opens) {
          this.router.navigate([PURCHASE_CONFIGS[opens].path, result.id]);
        } else {
          this.refresh();
        }
      });
    });
  }

  confirmDelete(document: PurchaseDocumentSummary) {
    this.confirmation.confirm({
      header: `Delete ${this.config.label.toLowerCase()} draft`,
      message: `This draft for ${document.supplierName} will be permanently removed. This cannot be undone.`,
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
