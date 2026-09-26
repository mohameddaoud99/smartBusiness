import { Component, ViewChild, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { BrandService } from '../../../core/services/brand.service';
import { NotificationService } from '../../../core/services/notification.service';
import { BrandResponse } from './brand.model';
import { BrandFormComponent } from './brand-form/brand-form.component';

@Component({
  selector: 'app-brands',
  standalone: true,
  imports: [
    TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, BrandFormComponent
  ],
  templateUrl: './brands.component.html',
  styleUrl: './brands.component.scss'
})
export class BrandsComponent {

  private readonly brandService = inject(BrandService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(BrandFormComponent) form!: BrandFormComponent;

  readonly brands = signal<BrandResponse[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.brandService.findAll().subscribe({
      next: brands => {
        this.brands.set(brands);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate() {
    this.form.open();
  }

  openEdit(brand: BrandResponse) {
    this.form.open(brand);
  }

  confirmDelete(brand: BrandResponse) {
    this.confirmation.confirm({
      header: 'Delete brand',
      message: `"${brand.name}" will be permanently removed. This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.brandService.delete(brand.id).subscribe(() => {
          this.notification.success(`"${brand.name}" has been deleted.`);
          this.load();
        });
      }
    });
  }
}
