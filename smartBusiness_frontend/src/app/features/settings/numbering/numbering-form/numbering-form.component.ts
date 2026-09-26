import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { CheckboxModule } from 'primeng/checkbox';

import { NumberingService } from '../../../../core/services/numbering.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { NumberingSequenceResponse } from '../numbering.model';

@Component({
  selector: 'app-numbering-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, InputNumberModule, CheckboxModule
  ],
  templateUrl: './numbering-form.component.html',
  styleUrl: './numbering-form.component.scss'
})
export class NumberingFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly numberingService = inject(NumberingService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  private readonly currentYear = new Date().getFullYear();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<NumberingSequenceResponse | null>(null);

  readonly title = computed(() => `${this.editing()?.documentTypeLabel ?? ''} numbering`);

  readonly form: FormGroup = this.fb.group({
    prefix: ['', [Validators.required, Validators.maxLength(10),
                  Validators.pattern(/^[A-Za-z0-9_-]+$/)]],
    padding: [5, [Validators.required, Validators.min(1), Validators.max(10)]],
    includeYear: [true],
    nextValue: [1, [Validators.required, Validators.min(1)]],
    active: [true]
  });

  /** Rebuilt on every keystroke by change detection — the same rule the backend applies. */
  get preview(): string {
    const { prefix, padding, includeYear, nextValue } = this.form.getRawValue();
    const width = Math.min(10, Math.max(1, Number(padding) || 1));
    const counter = String(Math.max(1, Number(nextValue) || 1)).padStart(width, '0');
    return `${prefix || '…'}${includeYear ? '-' + this.currentYear : ''}-${counter}`;
  }

  open(sequence: NumberingSequenceResponse) {
    this.editing.set(sequence);
    this.form.reset({
      prefix: sequence.prefix,
      padding: sequence.padding,
      includeYear: sequence.includeYear,
      nextValue: sequence.nextValue,
      active: sequence.active
    });
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

    const sequence = this.editing();
    if (!sequence) {
      return;
    }

    this.saving.set(true);
    this.numberingService.update(sequence.documentType, this.form.getRawValue()).subscribe({
      next: () => {
        this.notification.success('Numbering updated.');
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
    if (control.hasError('pattern')) return 'Letters, digits, underscore and hyphen only.';
    if (control.hasError('min')) return `Minimum ${control.getError('min').min}.`;
    if (control.hasError('max')) return `Maximum ${control.getError('max').max}.`;
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    return '';
  }
}
