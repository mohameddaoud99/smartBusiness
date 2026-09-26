import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';

import { BankAccountService } from '../../../../core/services/bank-account.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { CURRENCY_OPTIONS } from '../../company/company.model';
import { CompanyBankAccountResponse } from '../bank-account.model';

@Component({
  selector: 'app-bank-account-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, SelectModule, CheckboxModule
  ],
  templateUrl: './bank-account-form.component.html',
  styleUrl: './bank-account-form.component.scss'
})
export class BankAccountFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly bankAccountService = inject(BankAccountService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly currencyOptions = CURRENCY_OPTIONS;

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<CompanyBankAccountResponse | null>(null);

  readonly title = computed(() => this.editing() ? 'Edit bank account' : 'New bank account');

  readonly form: FormGroup = this.fb.group({
    label: ['', [Validators.required, Validators.maxLength(120)]],
    bankName: ['', Validators.maxLength(120)],
    rib: ['', [Validators.required, Validators.maxLength(34)]],
    currency: ['TND', Validators.required],
    showOnDocuments: [true]
  });

  open(account?: CompanyBankAccountResponse) {
    this.form.reset({
      label: '', bankName: '', rib: '', currency: 'TND', showOnDocuments: true
    });

    if (account) {
      this.editing.set(account);
      this.form.patchValue(account);
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
    const account = this.editing();
    const value = this.form.getRawValue();

    const request$ = account
      ? this.bankAccountService.update(account.id, value)
      : this.bankAccountService.create(value);

    request$.subscribe({
      next: () => {
        this.notification.success(
          account ? 'Bank account updated.' : 'Bank account added.'
        );
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
