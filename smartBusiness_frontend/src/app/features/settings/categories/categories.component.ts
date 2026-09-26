import { Component, ViewChild, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { CategoryService } from '../../../core/services/category.service';
import { NotificationService } from '../../../core/services/notification.service';
import { CategoryResponse } from './category.model';
import { CategoryFormComponent } from './category-form/category-form.component';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [
    TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, CategoryFormComponent
  ],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.scss'
})
export class CategoriesComponent {

  private readonly categoryService = inject(CategoryService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(CategoryFormComponent) form!: CategoryFormComponent;

  readonly categories = signal<CategoryResponse[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.categoryService.findAll().subscribe({
      next: categories => {
        this.categories.set(categories);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate() {
    this.form.open(this.categories());
  }

  openEdit(category: CategoryResponse) {
    this.form.open(this.categories(), category);
  }

  confirmDelete(category: CategoryResponse) {
    this.confirmation.confirm({
      header: 'Delete category',
      message: `"${category.name}" will be permanently removed. This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.categoryService.delete(category.id).subscribe(() => {
          this.notification.success(`"${category.name}" has been deleted.`);
          this.load();
        });
      }
    });
  }
}
