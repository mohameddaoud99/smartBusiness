import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';

import { WarehouseService } from '../../../../core/services/warehouse.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { WarehouseResponse } from '../warehouse.model';

@Component({
  selector: 'app-warehouse-form',
  standalone: true,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, CheckboxModule],
  templateUrl: './warehouse-form.component.html',
  styleUrl: './warehouse-form.component.scss'
})
export class WarehouseFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly warehouseService = inject(WarehouseService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<WarehouseResponse | null>(null);

  readonly title = computed(() => this.editing() ? 'Edit warehouse' : 'New warehouse');

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    address: ['', Validators.maxLength(255)],
    active: [true]
  });

  open(warehouse?: WarehouseResponse) {
    this.form.reset({ name: '', address: '', active: true });

    if (warehouse) {
      this.editing.set(warehouse);
      this.form.patchValue({
        name: warehouse.name,
        address: warehouse.address ?? '',
        active: warehouse.active
      });
      // The default warehouse can be renamed, never switched off
      if (warehouse.defaultWarehouse) {
        this.form.controls['active'].disable();
      }
    } else {
      this.editing.set(null);
      this.form.controls['active'].enable();
    }

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
    const warehouse = this.editing();
    const raw = this.form.getRawValue();
    const request = {
      name: raw.name,
      address: raw.address?.trim() || null,
      active: raw.active
    };

    const request$ = warehouse
      ? this.warehouseService.update(warehouse.id, request)
      : this.warehouseService.create(request);

    request$.subscribe({
      next: () => {
        this.notification.success(warehouse ? 'Warehouse updated.' : 'Warehouse created.');
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

  errorFor(field: string): string {
    const control = this.form.controls[field];
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    return '';
  }
}
