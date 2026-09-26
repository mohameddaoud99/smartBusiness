import { Component, inject, signal, ViewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MenuModule } from 'primeng/menu';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { CustomerService } from '../../core/services/customer.service';
import { NotificationService } from '../../core/services/notification.service';
import { CustomerResponse, CustomerType, TYPE_OPTIONS, typeLabel } from './customer.model';
import { CustomerFormComponent } from './customer-form/customer-form.component';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [
    DatePipe, FormsModule, TableModule, ButtonModule, InputTextModule,
    IconFieldModule, InputIconModule, SelectModule, TagModule, MenuModule,
    TooltipModule, PageHeaderComponent, HasPermissionDirective, CustomerFormComponent
  ],
  templateUrl: './customers.component.html',
  styleUrl: './customers.component.scss'
})
export class CustomersComponent {

  private readonly customerService = inject(CustomerService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(CustomerFormComponent) form!: CustomerFormComponent;

  readonly typeOptions = TYPE_OPTIONS;
  readonly typeLabel = typeLabel;

  readonly customers = signal<CustomerResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  searchTerm = '';
  typeFilter: CustomerType | null = null;

  rowMenuItems: MenuItem[] = [];

  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10 };
  private readonly searchInput = new Subject<string>();

  constructor() {
    this.searchInput
      .pipe(debounceTime(350), distinctUntilChanged())
      .subscribe(() => this.reload());
  }

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 10;
    this.customerService.search({
      search: this.searchTerm,
      type: this.typeFilter,
      page: (event.first ?? 0) / rows,
      size: rows,
      sortField: event.sortField as string,
      sortOrder: event.sortOrder ?? 1
    }).subscribe({
      next: page => {
        this.customers.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSearchChange() {
    this.searchInput.next(this.searchTerm);
  }

  onFilterChange() {
    this.reload();
  }

  clearFilters() {
    this.searchTerm = '';
    this.typeFilter = null;
    this.reload();
  }

  get hasFilters(): boolean {
    return !!this.searchTerm || !!this.typeFilter;
  }

  /** Back to page 1 — results change, so the current offset is meaningless. */
  reload() {
    this.load({ ...this.lastEvent, first: 0 });
  }

  refresh() {
    this.load(this.lastEvent);
  }

  openCreate() {
    this.form.open();
  }

  openEdit(customer: CustomerResponse) {
    this.form.open(customer);
  }

  buildRowMenu(customer: CustomerResponse) {
    this.rowMenuItems = [
      { label: 'Edit', icon: 'pi pi-pencil', command: () => this.openEdit(customer) },
      { separator: true },
      { label: 'Delete', icon: 'pi pi-trash', command: () => this.confirmDelete(customer) }
    ];
  }

  confirmDelete(customer: CustomerResponse) {
    this.confirmation.confirm({
      header: 'Delete customer',
      message: `${customer.name} will be permanently removed. This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.customerService.delete(customer.id).subscribe(() => {
          this.notification.success(`${customer.name} has been deleted.`);
          this.refresh();
        });
      }
    });
  }

  initials(customer: CustomerResponse): string {
    const parts = customer.name.trim().split(/\s+/);
    const letters = parts.length > 1 ? parts[0][0] + parts[1][0] : parts[0].slice(0, 2);
    return letters.toUpperCase();
  }
}
