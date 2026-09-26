import { Component, ViewChild, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { WarehouseService } from '../../../core/services/warehouse.service';
import { NotificationService } from '../../../core/services/notification.service';
import { WarehouseResponse } from './warehouse.model';
import { WarehouseFormComponent } from './warehouse-form/warehouse-form.component';

@Component({
  selector: 'app-warehouses',
  standalone: true,
  imports: [
    TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, WarehouseFormComponent
  ],
  templateUrl: './warehouses.component.html',
  styleUrl: './warehouses.component.scss'
})
export class WarehousesComponent {

  private readonly warehouseService = inject(WarehouseService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(WarehouseFormComponent) form!: WarehouseFormComponent;

  readonly warehouses = signal<WarehouseResponse[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.warehouseService.findAll().subscribe({
      next: warehouses => {
        this.warehouses.set(warehouses);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate() {
    this.form.open();
  }

  openEdit(warehouse: WarehouseResponse) {
    this.form.open(warehouse);
  }

  confirmDelete(warehouse: WarehouseResponse) {
    this.confirmation.confirm({
      header: 'Delete warehouse',
      message: `"${warehouse.name}" will be permanently removed. A warehouse that already has stock movements cannot be deleted — deactivate it instead.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.warehouseService.delete(warehouse.id).subscribe(() => {
          this.notification.success(`"${warehouse.name}" has been deleted.`);
          this.load();
        });
      }
    });
  }
}
