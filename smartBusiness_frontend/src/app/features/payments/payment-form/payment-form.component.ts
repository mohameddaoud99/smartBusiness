import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';

import { PaymentService } from '../../../core/services/payment.service';
import { SupplierPaymentService } from '../../../core/services/supplier-payment.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PAYMENT_METHODS, PaymentFamily, PaymentMethod } from '../payment.model';

/** The invoice a payment is for: what the dialog needs to name it and to cap the amount. */
export interface PayableInvoice {
  id: number;
  reference: string;
  /** What is still due — the payment cannot exceed it. */
  balance: number;
}

/**
 * The dialog that records money received on an invoice. It opens on the whole balance — the usual
 * case is a customer settling — and refuses more than what is due, as the server does.
 */
@Component({
  selector: 'app-payment-form',
  standalone: true,
  imports: [
    DecimalPipe, ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule,
    InputNumberModule, TextareaModule, SelectModule, DatePickerModule
  ],
  templateUrl: './payment-form.component.html',
  styleUrl: './payment-form.component.scss'
})
export class PaymentFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly customerPayments = inject(PaymentService);
  private readonly supplierPayments = inject(SupplierPaymentService);
  private readonly notification = inject(NotificationService);

  /** Whose payment it is: received from a customer, or paid to a supplier. */
  @Input() family: PaymentFamily = 'customer';
  @Output() saved = new EventEmitter<void>();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly invoice = signal<PayableInvoice | null>(null);

  readonly methods = PAYMENT_METHODS;

  readonly form: FormGroup = this.fb.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.001)]],
    paymentDate: [new Date() as Date | null, Validators.required],
    method: ['CASH' as PaymentMethod, Validators.required],
    reference: ['', Validators.maxLength(100)],
    notes: ['', Validators.maxLength(2000)]
  });

  open(invoice: PayableInvoice) {
    this.invoice.set(invoice);
    this.form.controls['amount'].setValidators([
      Validators.required, Validators.min(0.001), Validators.max(invoice.balance)
    ]);
    this.form.reset({ amount: invoice.balance, paymentDate: new Date(), method: 'CASH', reference: '', notes: '' });
    this.visible.set(true);
  }

  close() {
    this.visible.set(false);
  }

  submit() {
    const invoice = this.invoice();
    if (!invoice || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const value = this.form.getRawValue();
    const payments = this.family === 'supplier' ? this.supplierPayments : this.customerPayments;
    payments.create({
      invoiceId: invoice.id,
      amount: value.amount,
      paymentDate: toIso(value.paymentDate),
      method: value.method,
      reference: value.reference?.trim() || null,
      notes: value.notes?.trim() || null
    }).subscribe({
      next: () => {
        this.notification.success('Payment recorded.');
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
    if (control.hasError('min')) return 'The amount must be greater than zero.';
    if (control.hasError('max')) return 'The amount cannot be more than what is still due.';
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    return '';
  }
}

/** A Date → "2026-09-20", read in local time: `toISOString()` would shift the day. */
function toIso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
