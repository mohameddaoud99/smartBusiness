import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { Observable } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { StockService } from '../../../core/services/stock.service';
import { ProductService } from '../../../core/services/product.service';
import { WarehouseService } from '../../../core/services/warehouse.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ProductResponse } from '../../products/product.model';
import { WarehouseResponse } from '../../settings/warehouses/warehouse.model';
import { StockAction } from '../stock.model';

const ACTION_OPTIONS: { value: StockAction; label: string }[] = [
  { value: 'ENTRY', label: 'Entry — stock received' },
  { value: 'EXIT', label: 'Exit — stock taken out' },
  { value: 'ADJUSTMENT', label: 'Adjustment — inventory count' },
  { value: 'TRANSFER', label: 'Transfer — between warehouses' }
];

/**
 * One dialog for everything a person can do to the stock by hand: receive, take out, correct
 * after a count, or move between warehouses. The register is append-only, so each of these
 * writes new lines — nothing is ever edited.
 */
@Component({
  selector: 'app-stock-movement-form',
  standalone: true,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, InputNumberModule, SelectModule],
  templateUrl: './stock-movement-form.component.html',
  styleUrl: './stock-movement-form.component.scss'
})
export class StockMovementFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly stockService = inject(StockService);
  private readonly productService = inject(ProductService);
  private readonly warehouseService = inject(WarehouseService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly products = signal<ProductResponse[]>([]);
  readonly warehouses = signal<WarehouseResponse[]>([]);

  /** Only what the user is allowed to do: adjusting and transferring are separate permissions. */
  readonly actionOptions = computed(() => ACTION_OPTIONS.filter(option =>
    option.value === 'TRANSFER' ? this.auth.has('STOCK_TRANSFER') : this.auth.has('STOCK_ADJUST')));

  readonly form: FormGroup = this.fb.group({
    type: ['ENTRY' as StockAction, Validators.required],
    productId: [null as number | null, Validators.required],
    warehouseId: [null as number | null, Validators.required],
    toWarehouseId: [null as number | null],
    quantity: [null as number | null, [Validators.required, Validators.min(0.001)]],
    reason: ['', Validators.maxLength(255)]
  });

  constructor() {
    this.form.controls['type'].valueChanges.subscribe(type => this.applyType(type));
  }

  get isTransfer(): boolean {
    return this.form.value.type === 'TRANSFER';
  }

  get isAdjustment(): boolean {
    return this.form.value.type === 'ADJUSTMENT';
  }

  /** Opens the dialog, optionally already on a product (from a row of the inventory). */
  open(productId?: number) {
    const first = this.actionOptions()[0]?.value ?? 'ENTRY';
    this.form.reset({
      type: first, productId: productId ?? null, warehouseId: null, toWarehouseId: null,
      quantity: null, reason: ''
    });
    this.applyType(first);

    // Refreshed each time: a product or a warehouse may have been added since
    this.productService.search({ kind: 'GOOD', page: 0, size: 500, sortField: 'name', sortOrder: 1 })
      .subscribe(page => this.products.set(page.content));
    this.warehouseService.findAll().subscribe(list => {
      const active = list.filter(warehouse => warehouse.active);
      this.warehouses.set(active);
      // A single warehouse needs no choosing
      if (active.length === 1) {
        this.form.patchValue({ warehouseId: active[0].id });
      }
    });

    this.visible.set(true);
  }

  close() {
    this.visible.set(false);
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const value = this.form.getRawValue();
    const reason = value.reason?.trim() || null;

    const request$: Observable<unknown> = value.type === 'TRANSFER'
      ? this.stockService.transfer({
        productId: value.productId,
        fromWarehouseId: value.warehouseId,
        toWarehouseId: value.toWarehouseId,
        quantity: value.quantity,
        reason
      })
      : this.stockService.record({
        type: value.type,
        productId: value.productId,
        warehouseId: value.warehouseId,
        quantity: value.quantity,
        reason
      });

    request$.subscribe({
      next: () => {
        this.notification.success(value.type === 'TRANSFER' ? 'Stock transferred.' : 'Stock movement recorded.');
        this.saving.set(false);
        this.saved.emit();
        this.close();
      },
      error: () => this.saving.set(false)
    });
  }

  isInvalid(field: string): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  /** The destination is only asked for on a transfer; a count may legitimately be zero. */
  private applyType(type: StockAction) {
    const destination = this.form.controls['toWarehouseId'];
    const quantity = this.form.controls['quantity'];

    destination.setValidators(type === 'TRANSFER' ? Validators.required : null);
    quantity.setValidators([Validators.required, Validators.min(type === 'ADJUSTMENT' ? 0 : 0.001)]);
    destination.updateValueAndValidity({ emitEvent: false });
    quantity.updateValueAndValidity({ emitEvent: false });
  }
}
