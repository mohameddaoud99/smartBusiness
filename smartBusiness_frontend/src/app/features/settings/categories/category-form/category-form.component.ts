import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';

import { CategoryService } from '../../../../core/services/category.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { CategoryResponse } from '../category.model';

@Component({
  selector: 'app-category-form',
  standalone: true,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, SelectModule],
  templateUrl: './category-form.component.html',
  styleUrl: './category-form.component.scss'
})
export class CategoryFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly categoryService = inject(CategoryService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<CategoryResponse | null>(null);
  /** Every other category — a category cannot be parented under itself. */
  readonly parentOptions = signal<CategoryResponse[]>([]);

  readonly title = computed(() => this.editing() ? 'Edit category' : 'New category');

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    parentId: [null as number | null]
  });

  open(allCategories: CategoryResponse[], category?: CategoryResponse) {
    this.form.reset({ name: '', parentId: null });
    this.parentOptions.set(
      category ? allCategories.filter(candidate => candidate.id !== category.id) : allCategories
    );

    if (category) {
      this.editing.set(category);
      this.form.patchValue({ name: category.name, parentId: category.parentId ?? null });
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
    const category = this.editing();
    const value = this.form.getRawValue();

    const request$ = category
      ? this.categoryService.update(category.id, value)
      : this.categoryService.create(value);

    request$.subscribe({
      next: () => {
        this.notification.success(category ? 'Category updated.' : 'Category created.');
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
