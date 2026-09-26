import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';

import { BrandService } from '../../../../core/services/brand.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { BrandResponse } from '../brand.model';

@Component({
  selector: 'app-brand-form',
  standalone: true,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule],
  templateUrl: './brand-form.component.html',
  styleUrl: './brand-form.component.scss'
})
export class BrandFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly brandService = inject(BrandService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<BrandResponse | null>(null);

  readonly title = computed(() => this.editing() ? 'Edit brand' : 'New brand');

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]]
  });

  open(brand?: BrandResponse) {
    this.form.reset({ name: '' });

    if (brand) {
      this.editing.set(brand);
      this.form.patchValue({ name: brand.name });
    } else {
      this.editing.set(null);
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
    const brand = this.editing();
    const value = this.form.getRawValue();

    const request$ = brand
      ? this.brandService.update(brand.id, value)
      : this.brandService.create(value);

    request$.subscribe({
      next: () => {
        this.notification.success(brand ? 'Brand updated.' : 'Brand created.');
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
