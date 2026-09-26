import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators
} from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    ReactiveFormsModule, InputTextModule, PasswordModule, ButtonModule, PageHeaderComponent
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly notification = inject(NotificationService);

  readonly savingProfile = signal(false);
  readonly savingPassword = signal(false);

  /** The login identifier — shown for reference, changed only by an administrator. */
  readonly email = this.auth.session()?.email ?? '';

  readonly profileForm: FormGroup = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(60)]],
    lastName: ['', [Validators.required, Validators.maxLength(60)]],
    phone: ['', Validators.maxLength(30)]
  });

  readonly passwordForm: FormGroup = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirmPassword: ['', Validators.required]
  }, { validators: passwordsMatch });

  constructor() {
    const session = this.auth.session();
    this.profileForm.patchValue({
      firstName: session?.firstName ?? '',
      lastName: session?.lastName ?? '',
      phone: session?.phone ?? ''
    });
  }

  submitProfile() {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.savingProfile.set(true);
    this.auth.updateProfile(this.profileForm.getRawValue()).subscribe({
      next: () => {
        this.notification.success('Your profile has been updated.');
        this.savingProfile.set(false);
      },
      error: () => this.savingProfile.set(false)
    });
  }

  submitPassword() {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.savingPassword.set(true);
    this.auth.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.notification.success('Your password has been changed.');
        this.passwordForm.reset();
        this.savingPassword.set(false);
      },
      error: () => this.savingPassword.set(false)
    });
  }

  isInvalid(form: FormGroup, field: string): boolean {
    const control = form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }

  errorFor(form: FormGroup, field: string): string {
    const control = form.controls[field];
    if (control.hasError('required')) return 'This field is required.';
    if (control.hasError('minlength')) return 'Use at least 8 characters.';
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    return '';
  }

  get passwordsMismatch(): boolean {
    const control = this.passwordForm.controls['confirmPassword'];
    return this.passwordForm.hasError('mismatch') && (control.touched || control.dirty);
  }
}

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const next = group.get('newPassword')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return next && confirm && next !== confirm ? { mismatch: true } : null;
}
