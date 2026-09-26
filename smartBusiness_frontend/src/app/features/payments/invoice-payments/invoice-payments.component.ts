import { Component, EventEmitter, Input, OnChanges, Output, ViewChild, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ConfirmationService } from 'primeng/api';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { DocumentAction, confirmDocumentAction } from '../../../shared/document-action';
import { PaymentService } from '../../../core/services/payment.service';
import { SupplierPaymentService } from '../../../core/services/supplier-payment.service';
import { Permission } from '../../../core/auth/session.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PayableDocument, PaymentFamily, PaymentResponse, methodLabel } from '../payment.model';
import { PaymentFormComponent } from '../payment-form/payment-form.component';

/**
 * What has been paid on an invoice: the total, the paid amount and the balance (all from the server),
 * then the payments themselves. A payment is added here and, when wrong, cancelled — never edited.
 * Whenever the payments change it says so (`changed`), so the page reloads the invoice, whose status
 * has moved between unpaid, partly paid and paid.
 */
@Component({
  selector: 'app-invoice-payments',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TableModule, ButtonModule, TagModule, HasPermissionDirective, PaymentFormComponent],
  templateUrl: './invoice-payments.component.html',
  styleUrl: './invoice-payments.component.scss'
})
export class InvoicePaymentsComponent implements OnChanges {

  private readonly customerPayments = inject(PaymentService);
  private readonly supplierPayments = inject(SupplierPaymentService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @Input({ required: true }) document!: PayableDocument;
  /** Whose payments: what customers pay us (sales invoice), or what we pay suppliers (purchase invoice). */
  @Input() family: PaymentFamily = 'customer';
  @Output() changed = new EventEmitter<void>();

  @ViewChild(PaymentFormComponent) paymentForm!: PaymentFormComponent;

  readonly payments = signal<PaymentResponse[]>([]);
  readonly loading = signal(false);
  readonly methodLabel = methodLabel;

  ngOnChanges() {
    this.load();
  }

  private get payments$() {
    return this.family === 'supplier' ? this.supplierPayments : this.customerPayments;
  }

  /** Recording a payment is an update of the invoice, cancelling one a cancellation — of sales or of purchases. */
  get updatePermission(): Permission {
    return this.family === 'supplier' ? 'PURCHASE_UPDATE' : 'SALE_UPDATE';
  }

  get cancelPermission(): Permission {
    return this.family === 'supplier' ? 'PURCHASE_CANCEL' : 'SALE_CANCEL';
  }

  /** An unpaid or partly paid invoice takes payments; a paid or cancelled one no longer does. */
  get canAddPayment(): boolean {
    return ['ISSUED', 'VALIDATED', 'PARTIALLY_PAID'].includes(this.document.status);
  }

  addPayment() {
    this.paymentForm.open({
      id: this.document.id!,
      reference: this.document.reference ?? '',
      balance: this.document.balance ?? this.document.total
    });
  }

  confirmCancel(payment: PaymentResponse) {
    const action: DocumentAction = {
      id: 'cancelPayment', label: 'Cancel payment', icon: 'pi pi-ban', permission: this.cancelPermission,
      confirm: {
        header: 'Cancel payment',
        message: `The payment of ${payment.amount.toFixed(3)} will be cancelled and that amount is due on the invoice again. This cannot be undone.`,
        acceptLabel: 'Cancel payment',
        danger: true
      }
    };
    confirmDocumentAction(this.confirmation, action, () => {
      this.payments$.cancel(payment.id).subscribe(() => {
        this.notification.success('The payment has been cancelled.');
        this.changed.emit();
      });
    });
  }

  private load() {
    this.loading.set(true);
    this.payments$.findByInvoice(this.document.id!).subscribe({
      next: page => {
        this.payments.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
