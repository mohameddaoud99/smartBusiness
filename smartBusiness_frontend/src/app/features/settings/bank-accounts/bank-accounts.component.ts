import { Component, ViewChild, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { BankAccountService } from '../../../core/services/bank-account.service';
import { NotificationService } from '../../../core/services/notification.service';
import { CompanyBankAccountResponse } from './bank-account.model';
import { BankAccountFormComponent } from './bank-account-form/bank-account-form.component';

@Component({
  selector: 'app-bank-accounts',
  standalone: true,
  imports: [
    TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, BankAccountFormComponent
  ],
  templateUrl: './bank-accounts.component.html',
  styleUrl: './bank-accounts.component.scss'
})
export class BankAccountsComponent {

  private readonly bankAccountService = inject(BankAccountService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(BankAccountFormComponent) form!: BankAccountFormComponent;

  readonly accounts = signal<CompanyBankAccountResponse[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.bankAccountService.findAll().subscribe({
      next: accounts => {
        this.accounts.set(accounts);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate() {
    this.form.open();
  }

  openEdit(account: CompanyBankAccountResponse) {
    this.form.open(account);
  }

  confirmDelete(account: CompanyBankAccountResponse) {
    this.confirmation.confirm({
      header: 'Remove bank account',
      message: `"${account.label}" will no longer appear on your documents.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Remove',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.bankAccountService.delete(account.id).subscribe(() => {
          this.notification.success(`"${account.label}" has been removed.`);
          this.load();
        });
      }
    });
  }
}
