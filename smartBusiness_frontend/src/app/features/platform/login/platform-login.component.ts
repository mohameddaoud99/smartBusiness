import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';

import { PlatformAuthService } from '../../../core/platform-auth/platform-auth.service';

/**
 * Deliberately separate from the company /login screen — no "create account" link,
 * no shared layout. A platform admin account is never self-service.
 */
@Component({
  selector: 'app-platform-login',
  standalone: true,
  imports: [
    ReactiveFormsModule, ButtonModule, InputTextModule, PasswordModule, MessageModule
  ],
  templateUrl: './platform-login.component.html',
  styleUrl: './platform-login.component.scss'
})
export class PlatformLoginComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(PlatformAuthService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/platform/companies'),
      error: (response: HttpErrorResponse) => {
        this.errorMessage.set(response.error?.message ?? 'Unable to sign in. Please try again.');
        this.submitting.set(false);
      }
    });
  }

  isInvalid(field: string): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || control.dirty);
  }
}
