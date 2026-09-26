import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { DatePickerModule } from 'primeng/datepicker';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { EMPTY, Observable, catchError, debounceTime, filter, forkJoin, of, switchMap } from 'rxjs';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { openPrintPage } from '../../print/print-link';
import { confirmDocumentAction } from '../../../shared/document-action';
import {
  PurchaseAction, purchaseActionOpens, purchaseActionResult, purchaseActions, runPurchaseAction
} from '../purchase-document-actions';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { InvoicePaymentsComponent } from '../../payments/invoice-payments/invoice-payments.component';
import { PurchaseDocumentService } from '../../../core/services/purchase-document.service';
import { SupplierService } from '../../../core/services/supplier.service';
import { ProductService } from '../../../core/services/product.service';
import { TaxService } from '../../../core/services/tax.service';
import { WarehouseService } from '../../../core/services/warehouse.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ProductResponse } from '../../products/product.model';
import { TaxResponse } from '../../settings/taxes/tax.model';
import { WarehouseResponse } from '../../settings/warehouses/warehouse.model';
import {
  PURCHASE_CONFIGS, PurchaseDocumentLine, PurchaseDocumentRequest, PurchaseDocumentResponse,
  PurchaseDocumentType, fulfilledWord, hasWarehouse, takingWord, statusLabel, statusSeverity
} from '../purchase-document.model';

/** A button in the page header: what can be done with the document in its current status. */
interface HeaderAction extends PurchaseAction {
  run: () => void;
}

interface SupplierOption {
  id: number;
  name: string;
}

/**
 * One page to write, read and act on a purchase order or a goods receipt — the route says which
 * (`data.documentType`). The mirror of the sales editor: a draft is an editable form with live
 * totals, a validated document is read-only. The totals are never computed here — every change is
 * sent to the preview endpoint, so the calculation is written once, on the server.
 *
 * What is specific: a goods receipt names the warehouse its goods go into, and validating it is
 * what adds them to the stock.
 */
@Component({
  selector: 'app-purchase-document-editor',
  standalone: true,
  imports: [
    DecimalPipe, RouterLink, ReactiveFormsModule, ButtonModule, InputTextModule,
    InputNumberModule, TextareaModule, SelectModule, MultiSelectModule, DatePickerModule,
    TagModule, TooltipModule, PageHeaderComponent, HasPermissionDirective, InvoicePaymentsComponent
  ],
  templateUrl: './purchase-document-editor.component.html',
  styleUrl: './purchase-document-editor.component.scss'
})
export class PurchaseDocumentEditorComponent {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly documentService = inject(PurchaseDocumentService);
  private readonly supplierService = inject(SupplierService);
  private readonly productService = inject(ProductService);
  private readonly taxService = inject(TaxService);
  private readonly warehouseService = inject(WarehouseService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  readonly type: PurchaseDocumentType = this.route.snapshot.data['documentType'];
  readonly config = PURCHASE_CONFIGS[this.type];
  readonly statusLabel = statusLabel;
  /** A goods receipt and an invoice name the warehouse their goods go into. */
  readonly hasWarehouse = hasWarehouse(this.type);
  /** What the quantity of a draft that follows a source line may still be, in words: "to deliver", "to return"... */
  readonly takingWord = takingWord(this.type);
  readonly statusSeverity = statusSeverity;
  readonly configs = PURCHASE_CONFIGS;

  readonly loading = signal(true);
  readonly saving = signal(false);
  /** The saved document — null while creating. */
  readonly document = signal<PurchaseDocumentResponse | null>(null);
  /** The totals of what is on screen now, computed by the server without saving. */
  readonly preview = signal<PurchaseDocumentResponse | null>(null);

  readonly suppliers = signal<SupplierOption[]>([]);
  readonly products = signal<ProductResponse[]>([]);
  readonly taxes = signal<TaxResponse[]>([]);
  readonly warehouses = signal<WarehouseResponse[]>([]);

  readonly purchasableProducts = computed(() => this.products().filter(p => p.purpose !== 'SALE'));
  readonly vatTaxes = computed(() => this.taxes().filter(t => t.kind === 'VAT_RATE' && t.active));
  /** Surcharges and flat charges — VAT is picked on each line instead. */
  readonly documentTaxes = computed(() => this.taxes().filter(t => t.kind !== 'VAT_RATE' && t.active));

  readonly isNew = computed(() => this.document() === null);
  readonly readOnly = computed(() => {
    const current = this.document();
    return current !== null && current.status !== 'DRAFT';
  });
  readonly totals = computed(() => this.readOnly() ? this.document() : (this.preview() ?? this.document()));

  readonly title = computed(() => {
    const current = this.document();
    if (!current) {
      return `New ${this.config.label.toLowerCase()}`;
    }
    return current.reference ?? `${this.config.label} draft`;
  });

  readonly actions = computed<HeaderAction[]>(() => this.buildActions(this.document()));

  readonly form: FormGroup = this.fb.group({
    supplierId: [null as number | null, Validators.required],
    warehouseId: [null as number | null],
    issueDate: [new Date() as Date | null, Validators.required],
    dueDate: [null as Date | null],
    taxIds: [[] as number[]],
    notes: ['', Validators.maxLength(4000)],
    terms: ['', Validators.maxLength(4000)],
    lines: this.fb.array<FormGroup>([])
  });

  constructor() {
    // Only a receipt and an invoice have a warehouse — and there it is required
    if (this.hasWarehouse) {
      this.form.controls['warehouseId'].addValidators(Validators.required);
    }

    forkJoin({
      taxes: this.taxService.findAll(),
      // A company's catalogue and supplier base are small enough to load once; the supplier
      // list is refined by a server-side search as the user types (see onSupplierFilter)
      products: this.productService.search({ page: 0, size: 500, sortField: 'name', sortOrder: 1 }),
      suppliers: this.supplierService.search({ page: 0, size: 50, sortField: 'name', sortOrder: 1 }),
      warehouses: this.hasWarehouse ? this.warehouseService.findAll() : of([] as WarehouseResponse[])
    }).pipe(
      switchMap(({ taxes, products, suppliers, warehouses }) => {
        this.taxes.set(taxes);
        this.products.set(products.content);
        this.suppliers.set(suppliers.content.map(s => ({ id: s.id, name: s.name })));
        this.warehouses.set(warehouses.filter(w => w.active));
        return this.route.paramMap;
      }),
      switchMap(params => {
        const id = params.get('id');
        return id ? this.documentService.findById(+id) : of(null);
      }),
      takeUntilDestroyed()
    ).subscribe({
      next: document => {
        if (document) {
          this.apply(document);
        } else {
          this.startNew();
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    this.form.valueChanges.pipe(
      debounceTime(300),
      filter(() => !this.readOnly() && this.form.valid && this.lines.length > 0),
      switchMap(() => this.documentService.preview(this.buildRequest()).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed()
    ).subscribe(preview => this.preview.set(preview));
  }

  get lines(): FormArray<FormGroup> {
    return this.form.get('lines') as FormArray<FormGroup>;
  }

  pathFor(type: PurchaseDocumentType): string {
    return PURCHASE_CONFIGS[type].path;
  }

  /** A saved document's line: how much has been taken of it so far and what is left — for the documents that are followed. */
  fulfilment(index: number): string | null {
    const line = this.document()?.lines[index];
    if (!this.readOnly() || line?.fulfilledQuantity == null) {
      return null;
    }
    return `${fulfilledWord(this.type)} ${line.fulfilledQuantity} · ${line.remainingQuantity} left`;
  }

  /** A draft that follows a source line: how much of that line can still be taken (this draft excluded). */
  sourceLeft(index: number): number | null {
    return this.lines.at(index).value.sourceRemaining ?? null;
  }

  lineTotal(index: number): number | undefined {
    return this.totals()?.lines[index]?.lineTotal;
  }

  /** Opens the print preview of the saved document in a new tab. */
  print() {
    openPrintPage(this.router, 'purchases', this.document()!.id!);
  }

  // ----- Form -----

  addLine() {
    this.lines.push(this.newLine());
  }

  removeLine(index: number) {
    this.lines.removeAt(index);
  }

  /** A picked product fills the line at its PURCHASE price; clearing the pick leaves a free line. */
  onProductPicked(index: number, productId: number | null) {
    const line = this.lines.at(index);
    const product = this.products().find(p => p.id === productId);
    if (!product) {
      line.patchValue({ productId: null, reference: null });
      return;
    }
    const defaultVat = this.vatTaxes().find(vat => product.defaultTaxes.some(t => t.id === vat.id));
    line.patchValue({
      reference: product.reference,
      designation: product.name,
      unitPrice: product.purchasePrice ?? 0,
      vatTaxId: defaultVat?.id ?? this.defaultVatId()
    });
  }

  onSupplierFilter(term: string) {
    this.supplierService.search({ search: term, page: 0, size: 50, sortField: 'name', sortOrder: 1 })
      .subscribe(page => {
        const found = page.content.map(s => ({ id: s.id, name: s.name }));
        // keep the selected supplier in the list, or the select would lose its label
        const selected = this.suppliers().find(s => s.id === this.form.value.supplierId);
        this.suppliers.set(selected && !found.some(s => s.id === selected.id) ? [selected, ...found] : found);
      });
  }

  isInvalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  // ----- Draft actions -----

  saveDraft() {
    const call = this.persist();
    if (!call) {
      return;
    }
    this.saving.set(true);
    call.subscribe({
      next: saved => this.afterSave(saved, 'Draft saved.'),
      error: () => this.saving.set(false)
    });
  }

  confirmValidate() {
    if (this.form.invalid || this.lines.length === 0) {
      this.form.markAllAsTouched();
      this.notification.warn('Please fill in the required fields first.');
      return;
    }
    // The same words as the "Validate" of the list menu — one definition of the step
    const [validate] = purchaseActions({ type: this.type, status: 'DRAFT' });
    confirmDocumentAction(this.confirmation, validate, () => this.validate());
  }

  confirmDelete() {
    this.confirmation.confirm({
      header: `Delete ${this.config.label.toLowerCase()} draft`,
      message: 'This draft will be permanently removed. This cannot be undone.',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.documentService.delete(this.document()!.id!).subscribe(() => {
          this.notification.success('The draft has been deleted.');
          this.router.navigate([this.config.path]);
        });
      }
    });
  }

  private validate() {
    // What is on screen is saved first, so the number is given to what the user sees
    const call = this.persist();
    if (!call) {
      return;
    }
    this.saving.set(true);
    call.pipe(switchMap(saved => this.documentService.validate(saved.id!))).subscribe({
      next: validated => this.afterSave(validated,
        this.type === 'GOODS_RECEIPT'
          ? `Goods receipt ${validated.reference} validated — the goods are in stock.`
          : `${this.config.label} validated as ${validated.reference}.`),
      error: () => this.saving.set(false)
    });
  }

  /** After a payment was recorded or cancelled: the invoice moved from unpaid to paid, or back. */
  reloadDocument() {
    this.documentService.findById(this.document()!.id!).subscribe(document => this.apply(document));
  }

  // ----- Actions on a validated document -----

  private buildActions(current: PurchaseDocumentResponse | null): HeaderAction[] {
    if (!current || current.status === 'DRAFT') {
      return [];
    }
    return purchaseActions(current).map(action => ({ ...action, run: () => this.runStep(action) }));
  }

  /** Every step asks first, then goes to the server and shows what it did. */
  private runStep(action: PurchaseAction) {
    confirmDocumentAction(this.confirmation, action, () => {
      runPurchaseAction(this.documentService, this.document()!.id!, action.id).subscribe(result => {
        this.notification.success(purchaseActionResult(action.id, result));
        const opens = purchaseActionOpens(action.id);
        if (opens) {
          this.router.navigate([PURCHASE_CONFIGS[opens].path, result.id]);
        } else {
          this.apply(result);
        }
      });
    });
  }

  // ----- State -----

  private persist(): Observable<PurchaseDocumentResponse> | null {
    if (this.form.invalid || this.lines.length === 0) {
      this.form.markAllAsTouched();
      this.notification.warn('Please fill in the required fields first.');
      return null;
    }
    const request = this.buildRequest();
    const current = this.document();
    return current ? this.documentService.update(current.id!, request) : this.documentService.create(request);
  }

  private afterSave(saved: PurchaseDocumentResponse, message: string) {
    this.saving.set(false);
    this.notification.success(message);
    if (this.isNew()) {
      // Another route: the editor of the saved document takes over
      this.router.navigate([this.config.path, saved.id], { replaceUrl: true });
    } else {
      this.apply(saved);
    }
  }

  private startNew() {
    this.document.set(null);
    this.preview.set(null);
    this.lines.clear({ emitEvent: false });
    this.lines.push(this.newLine(), { emitEvent: false });
    this.form.enable({ emitEvent: false });
    this.form.patchValue({
      supplierId: null,
      // A receipt or an invoice starts on the default warehouse — one less thing to pick
      warehouseId: this.warehouses().find(w => w.defaultWarehouse)?.id ?? null,
      issueDate: new Date(),
      dueDate: null,
      // The taxes the company ticked "active by default" — FODEC and stamp duty for a Tunisian company
      taxIds: this.documentTaxes().filter(t => t.activeByDefault).map(t => t.id),
      notes: '',
      terms: ''
    }, { emitEvent: false });
  }

  private apply(document: PurchaseDocumentResponse) {
    this.document.set(document);
    this.preview.set(null);

    if (document.supplierId && !this.suppliers().some(s => s.id === document.supplierId)) {
      this.suppliers.update(list => [{ id: document.supplierId!, name: document.supplierName ?? '' }, ...list]);
    }
    // A validated document may sit in a warehouse deactivated since: keep it shown
    if (document.warehouseId && !this.warehouses().some(w => w.id === document.warehouseId)) {
      this.warehouses.update(list => [...list, {
        id: document.warehouseId!, name: document.warehouseName ?? '', defaultWarehouse: false,
        active: false, createdAt: '', updatedAt: ''
      }]);
    }

    this.lines.clear({ emitEvent: false });
    document.lines.forEach(line => this.lines.push(this.newLine(line), { emitEvent: false }));
    this.form.patchValue({
      supplierId: document.supplierId,
      warehouseId: document.warehouseId ?? null,
      issueDate: toDate(document.issueDate),
      dueDate: toDate(document.dueDate),
      taxIds: document.taxes.filter(t => t.taxId).map(t => t.taxId!),
      notes: document.notes ?? '',
      terms: document.terms ?? ''
    }, { emitEvent: false });

    if (document.status === 'DRAFT') {
      this.form.enable({ emitEvent: false });
      // A credit note stays with the supplier of its invoice
      if (this.type === 'PURCHASE_CREDIT_NOTE') {
        this.form.controls['supplierId'].disable({ emitEvent: false });
      }
    } else {
      this.form.disable({ emitEvent: false });
    }
  }

  private newLine(line?: PurchaseDocumentLine): FormGroup {
    return this.fb.group({
      productId: [line?.productId ?? null],
      reference: [line?.reference ?? null],
      designation: [line?.designation ?? '', [Validators.required, Validators.maxLength(255)]],
      // A line that follows a source line cannot take more than what is left of it
      quantity: [line?.quantity ?? 1, [Validators.required, Validators.min(0.001),
        ...(line?.sourceRemaining != null ? [Validators.max(line.sourceRemaining)] : [])]],
      sourceLineId: [line?.sourceLineId ?? null],
      sourceRemaining: [line?.sourceRemaining ?? null],
      unitPrice: [line?.unitPrice ?? 0, [Validators.required, Validators.min(0)]],
      discountRate: [line?.discountRate ?? 0, [Validators.min(0), Validators.max(100)]],
      vatTaxId: [line ? (line.vatTaxId ?? null) : this.defaultVatId()]
    });
  }

  private defaultVatId(): number | null {
    return this.vatTaxes().find(t => t.activeByDefault)?.id ?? null;
  }

  private buildRequest(): PurchaseDocumentRequest {
    const value = this.form.getRawValue();
    return {
      type: this.type,
      supplierId: value.supplierId,
      warehouseId: this.hasWarehouse ? value.warehouseId : null,
      issueDate: toIso(value.issueDate)!,
      dueDate: toIso(value.dueDate),
      notes: value.notes || null,
      terms: value.terms || null,
      taxIds: value.taxIds ?? [],
      lines: (value.lines as PurchaseDocumentLine[]).map(line => ({
        productId: line.productId ?? null,
        reference: line.reference || null,
        designation: line.designation,
        quantity: line.quantity,
        sourceLineId: line.sourceLineId ?? null,
        unitPrice: line.unitPrice,
        discountRate: line.discountRate ?? 0,
        vatTaxId: line.vatTaxId ?? null
      }))
    };
  }
}

/** "2026-09-20" → a local Date (not UTC: `new Date('2026-09-20')` would shift the day). */
function toDate(iso?: string | null): Date | null {
  if (!iso) {
    return null;
  }
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** A Date → "2026-09-20", read in local time for the same reason. */
function toIso(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
