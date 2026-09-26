import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';

import { TaxService } from '../../../../core/services/tax.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { TAX_KIND_OPTIONS, TaxKind, TaxRequest, TaxResponse } from '../tax.model';

@Component({
  selector: 'app-tax-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, InputNumberModule,
    SelectModule, CheckboxModule
  ],
  templateUrl: './tax-form.component.html',
  styleUrl: './tax-form.component.scss'
})
export class TaxFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly taxService = inject(TaxService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly kindOptions = TAX_KIND_OPTIONS;

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<TaxResponse | null>(null);

  readonly title = computed(() => this.editing() ? 'Edit tax' : 'New tax');

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(60)]],
    kind: ['VAT_RATE' as TaxKind, Validators.required],
    rate: [null as number | null],
    amount: [null as number | null],
    includedInVatBase: [false],
    activeByDefault: [true],
    active: [true]
  });

  get kind(): TaxKind {
    return this.form.controls['kind'].value;
  }

  get isFixed(): boolean {
    return this.kind === 'FIXED_PER_DOCUMENT';
  }

  get isSurcharge(): boolean {
    return this.kind === 'PERCENTAGE_SURCHARGE';
  }

  open(tax?: TaxResponse) {
    this.form.reset({
      name: '', kind: 'VAT_RATE', rate: null, amount: null,
      includedInVatBase: false, activeByDefault: true, active: true
    });

    if (tax) {
      this.editing.set(tax);
      this.form.patchValue(tax);
      this.form.controls['kind'].disable();
    } else {
      this.editing.set(null);
      this.form.controls['kind'].enable();
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

    const raw = this.form.getRawValue();
    const request: TaxRequest = {
      name: raw.name,
      kind: raw.kind,
      rate: this.isFixed ? null : raw.rate,
      amount: this.isFixed ? raw.amount : null,
      includedInVatBase: this.isSurcharge ? raw.includedInVatBase : false,
      activeByDefault: raw.activeByDefault,
      active: raw.active
    };

    if ((request.rate === null || request.rate === undefined) && !this.isFixed) {
      this.form.controls['rate'].setErrors({ required: true });
      return;
    }
    if ((request.amount === null || request.amount === undefined) && this.isFixed) {
      this.form.controls['amount'].setErrors({ required: true });
      return;
    }

    this.saving.set(true);
    const tax = this.editing();
    const request$ = tax
      ? this.taxService.update(tax.id, request)
      : this.taxService.create(request);

    request$.subscribe({
      next: () => {
        this.notification.success(tax ? 'Tax updated.' : 'Tax created.');
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
}
