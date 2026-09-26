import { Component, ViewChild, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { TaxService } from '../../../core/services/tax.service';
import { NotificationService } from '../../../core/services/notification.service';
import { TAX_KIND_LABELS, TaxResponse } from './tax.model';
import { TaxFormComponent } from './tax-form/tax-form.component';

@Component({
  selector: 'app-taxes',
  standalone: true,
  imports: [
    DecimalPipe, TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, TaxFormComponent
  ],
  templateUrl: './taxes.component.html',
  styleUrl: './taxes.component.scss'
})
export class TaxesComponent {

  private readonly taxService = inject(TaxService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(TaxFormComponent) form!: TaxFormComponent;

  readonly kindLabels: Record<string, string> = TAX_KIND_LABELS;

  readonly taxes = signal<TaxResponse[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.taxService.findAll().subscribe({
      next: taxes => {
        this.taxes.set(taxes);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate() {
    this.form.open();
  }

  openEdit(tax: TaxResponse) {
    this.form.open(tax);
  }

  confirmDelete(tax: TaxResponse) {
    this.confirmation.confirm({
      header: 'Delete tax',
      message: `"${tax.name}" will no longer be available on your documents.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.taxService.delete(tax.id).subscribe(() => {
          this.notification.success(`"${tax.name}" has been deleted.`);
          this.load();
        });
      }
    });
  }
}
