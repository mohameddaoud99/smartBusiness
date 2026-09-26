import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { CompanyService } from '../../../core/services/company.service';
import { NotificationService } from '../../../core/services/notification.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CURRENCY_OPTIONS, CompanyResponse } from './company.model';
import { ImageUploadComponent } from '../../../shared/components/image-upload/image-upload.component';

@Component({
  selector: 'app-company-settings',
  standalone: true,
  imports: [
    ReactiveFormsModule, InputTextModule, SelectModule, ButtonModule, SkeletonModule,
    PageHeaderComponent, ImageUploadComponent
  ],
  templateUrl: './company-settings.component.html',
  styleUrl: './company-settings.component.scss'
})
export class CompanySettingsComponent {

  private readonly fb = inject(FormBuilder);
  private readonly companyService = inject(CompanyService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly auth = inject(AuthService);

  readonly currencyOptions = CURRENCY_OPTIONS;

  readonly company = signal<CompanyResponse | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly logoSaving = signal(false);
  readonly stampSaving = signal(false);

  readonly canEdit = this.auth.has('COMPANY_UPDATE');

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    phone: ['', Validators.maxLength(30)],
    address: ['', Validators.maxLength(255)],
    postalCode: ['', Validators.maxLength(20)],
    city: ['', Validators.maxLength(100)],
    taxId: ['', Validators.maxLength(30)],
    currency: ['TND', Validators.required]
  });

  constructor() {
    this.load();
    if (!this.canEdit) {
      this.form.disable();
    }
  }

  load() {
    this.loading.set(true);
    this.companyService.find().subscribe({
      next: company => {
        this.company.set(company);
        this.form.patchValue(company);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.companyService.update(this.form.getRawValue()).subscribe({
      next: company => {
        this.company.set(company);
        this.notification.success('Company settings saved.');
        this.saving.set(false);
      },
      error: () => this.saving.set(false)
    });
  }

  onLogoSelected(file: File) {
    this.logoSaving.set(true);
    this.companyService.uploadLogo(file).subscribe({
      next: company => {
        this.company.set(company);
        this.notification.success('Logo updated.');
        this.logoSaving.set(false);
      },
      error: () => this.logoSaving.set(false)
    });
  }

  confirmRemoveLogo() {
    this.confirmation.confirm({
      header: 'Remove logo',
      message: 'The logo will be removed from your documents and the application header.',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Remove',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.logoSaving.set(true);
        this.companyService.removeLogo().subscribe({
          next: company => {
            this.company.set(company);
            this.notification.success('Logo removed.');
            this.logoSaving.set(false);
          },
          error: () => this.logoSaving.set(false)
        });
      }
    });
  }

  onStampSelected(file: File) {
    this.stampSaving.set(true);
    this.companyService.uploadStamp(file).subscribe({
      next: company => {
        this.company.set(company);
        this.notification.success('Stamp updated.');
        this.stampSaving.set(false);
      },
      error: () => this.stampSaving.set(false)
    });
  }

  confirmRemoveStamp() {
    this.confirmation.confirm({
      header: 'Remove stamp',
      message: 'The company stamp will be removed and will no longer appear on documents.',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Remove',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.stampSaving.set(true);
        this.companyService.removeStamp().subscribe({
          next: company => {
            this.company.set(company);
            this.notification.success('Stamp removed.');
            this.stampSaving.set(false);
          },
          error: () => this.stampSaving.set(false)
        });
      }
    });
  }

  isInvalid(field: string): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  errorFor(field: string): string {
    const control = this.form.controls[field];
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('email')) return 'Enter a valid email address.';
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    return '';
  }
}
