import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';

import { BranchService } from '../../../core/services/branch.service';
import { NotificationService } from '../../../core/services/notification.service';
import { BranchResponse } from '../branch.model';

@Component({
  selector: 'app-branch-form',
  standalone: true,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule],
  templateUrl: './branch-form.component.html',
  styleUrl: './branch-form.component.scss'
})
export class BranchFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly branchService = inject(BranchService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<BranchResponse | null>(null);

  readonly title = computed(() => this.editing() ? 'Edit branch' : 'New branch');

  readonly form: FormGroup = this.fb.group({
    code: ['', [Validators.required, Validators.maxLength(20),
                Validators.pattern(/^[A-Za-z0-9_-]+$/)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    address: ['', Validators.maxLength(255)],
    phone: ['', Validators.maxLength(30)]
  });

  open(branch?: BranchResponse) {
    this.form.reset({ code: '', name: '', address: '', phone: '' });

    if (branch) {
      this.editing.set(branch);
      this.form.patchValue(branch);
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
    const branch = this.editing();
    const value = this.form.getRawValue();

    const request$ = branch
      ? this.branchService.update(branch.id, value)
      : this.branchService.create(value);

    request$.subscribe({
      next: () => {
        this.notification.success(
          branch ? 'Branch updated successfully.' : 'Branch created successfully.'
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
    if (control.hasError('pattern')) {
      return 'Only letters, digits, underscore and hyphen are allowed.';
    }
    return '';
  }
}
