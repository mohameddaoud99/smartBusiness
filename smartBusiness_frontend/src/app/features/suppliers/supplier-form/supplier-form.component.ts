import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { RadioButtonModule } from 'primeng/radiobutton';
import { DatePickerModule } from 'primeng/datepicker';
import { CheckboxModule } from 'primeng/checkbox';

import { SupplierService } from '../../../core/services/supplier.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Address, SupplierRequest, SupplierResponse, SupplierType, TYPE_OPTIONS } from '../supplier.model';

@Component({
  selector: 'app-supplier-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, TextareaModule,
    RadioButtonModule, DatePickerModule, CheckboxModule
  ],
  templateUrl: './supplier-form.component.html',
  styleUrl: './supplier-form.component.scss'
})
export class SupplierFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly supplierService = inject(SupplierService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly typeOptions = TYPE_OPTIONS;
  readonly maxBirthDate = new Date();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editingId = signal<number | null>(null);

  readonly form: FormGroup = this.fb.group({
    type: ['COMPANY' as SupplierType, Validators.required],
    reference: ['', Validators.maxLength(20)],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    contactName: ['', Validators.maxLength(120)],
    taxId: ['', Validators.maxLength(30)],
    nationalId: ['', Validators.maxLength(30)],
    birthDate: [null as Date | null],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    phone: ['', Validators.maxLength(30)],
    billingAddress: this.addressGroup(),
    sameShipping: [true],
    shippingAddress: this.addressGroup(),
    notes: ['', Validators.maxLength(2000)]
  });

  private addressGroup(): FormGroup {
    return this.fb.group({
      street: ['', Validators.maxLength(255)],
      city: ['', Validators.maxLength(100)],
      region: ['', Validators.maxLength(100)],
      postalCode: ['', Validators.maxLength(20)],
      country: ['Tunisia', Validators.maxLength(60)]
    });
  }

  get isCompany(): boolean {
    return this.form.controls['type'].value === 'COMPANY';
  }

  get sameShipping(): boolean {
    return this.form.controls['sameShipping'].value;
  }

  get nameLabel(): string {
    return this.isCompany ? 'Business name' : 'Full name';
  }

  open(supplier?: SupplierResponse) {
    this.form.reset({
      type: 'COMPANY',
      reference: '',
      sameShipping: true,
      billingAddress: { country: 'Tunisia' },
      shippingAddress: { country: 'Tunisia' }
    });

    if (supplier) {
      this.editingId.set(supplier.id);
      const hasShipping = !!supplier.shippingAddress;
      this.form.patchValue({
        ...supplier,
        birthDate: supplier.birthDate ? new Date(supplier.birthDate + 'T00:00:00') : null,
        billingAddress: supplier.billingAddress ?? { country: 'Tunisia' },
        shippingAddress: supplier.shippingAddress ?? { country: 'Tunisia' },
        sameShipping: !hasShipping
      });
    } else {
      this.editingId.set(null);
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
    const raw = this.form.getRawValue();
    const company = raw.type === 'COMPANY';

    const request: SupplierRequest = {
      type: raw.type,
      reference: raw.reference?.trim() || undefined,
      name: raw.name,
      contactName: company ? raw.contactName || undefined : undefined,
      taxId: company ? raw.taxId || undefined : undefined,
      nationalId: company ? undefined : raw.nationalId || undefined,
      birthDate: company ? null : this.toIsoDate(raw.birthDate),
      email: raw.email || undefined,
      phone: raw.phone || undefined,
      billingAddress: this.cleanAddress(raw.billingAddress),
      shippingAddress: raw.sameShipping ? null : this.cleanAddress(raw.shippingAddress),
      notes: raw.notes || undefined
    };

    const id = this.editingId();
    const request$ = id
      ? this.supplierService.update(id, request)
      : this.supplierService.create(request);

    request$.subscribe({
      next: () => {
        this.notification.success(
          id ? 'Supplier updated successfully.' : 'Supplier created successfully.'
        );
        this.saving.set(false);
        this.saved.emit();
        this.close();
      },
      error: () => this.saving.set(false)
    });
  }

  /** Only a real street/city/region/postal code counts — a lone default country does not. */
  private cleanAddress(value: Address): Address | null {
    const { country, ...rest } = value;
    const hasContent = Object.values(rest).some(part => (part ?? '').toString().trim().length > 0);
    return hasContent ? value : null;
  }

  private toIsoDate(value: Date | null): string | null {
    if (!value) {
      return null;
    }
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  isInvalid(field: string): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  errorFor(field: string): string {
    const control = this.form.controls[field];
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('email')) return 'Enter a valid email address.';
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    return '';
  }
}
