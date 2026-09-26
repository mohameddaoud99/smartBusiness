import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, FormGroup } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';

import { UserService } from '../../../core/services/user.service';
import { RoleService } from '../../../core/services/role.service';
import { BranchService } from '../../../core/services/branch.service';
import { NotificationService } from '../../../core/services/notification.service';
import { AuthService } from '../../../core/auth/auth.service';
import { RoleResponse } from '../../roles/role.model';
import { BranchResponse } from '../../branches/branch.model';
import { UserResponse, STATUS_OPTIONS } from '../user.model';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, DialogModule, ButtonModule,
    InputTextModule, SelectModule, MultiSelectModule, MessageModule
  ],
  templateUrl: './user-form.component.html',
  styleUrl: './user-form.component.scss'
})
export class UserFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly roleService = inject(RoleService);
  private readonly branchService = inject(BranchService);
  private readonly notification = inject(NotificationService);
  private readonly auth = inject(AuthService);

  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() saved = new EventEmitter<void>();

  readonly statusOptions = STATUS_OPTIONS;
  readonly roles = signal<RoleResponse[]>([]);
  readonly branches = signal<BranchResponse[]>([]);

  readonly saving = signal(false);
  readonly editingId = signal<number | null>(null);

  /** Editing yourself: the backend refuses a role change, so the field is disabled here. */
  readonly editingSelf = signal(false);

  readonly form: FormGroup = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(60)]],
    lastName: ['', [Validators.required, Validators.maxLength(60)]],
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50),
                    Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    phone: ['', [Validators.maxLength(30)]],
    branchId: [null as number | null],
    roleIds: [[] as number[]],
    status: ['ACTIVE', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  constructor() {
    this.roleService.findAll().subscribe(roles => this.roles.set(roles));
    this.branchService.findAllForPicker().subscribe(branches => this.branches.set(branches));
  }

  /** Called by the parent to open the dialog in create or edit mode. */
  open(user?: UserResponse) {
    this.form.reset({ status: 'ACTIVE', phone: '', branchId: null, roleIds: [] });
    this.form.controls['roleIds'].enable();

    if (user) {
      this.editingId.set(user.id);
      this.editingSelf.set(user.id === this.auth.session()?.id);
      this.form.patchValue({
        ...user,
        branchId: user.branch?.id ?? null,
        roleIds: user.roles.map(role => role.id)
      });
      // Password is only set at creation — reset it through the dedicated action
      this.form.controls['password'].clearValidators();
      if (this.editingSelf()) {
        this.form.controls['roleIds'].disable();
      }
    } else {
      this.editingId.set(null);
      this.editingSelf.set(false);
      this.form.controls['password'].setValidators([Validators.required, Validators.minLength(8)]);
    }
    this.form.controls['password'].updateValueAndValidity();

    this.visible = true;
    this.visibleChange.emit(true);
  }

  close() {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const id = this.editingId();
    // getRawValue keeps disabled controls, so a self-edit still sends the current roles
    const value = this.form.getRawValue();

    const request$ = id
      ? this.userService.update(id, value)
      : this.userService.create(value);

    request$.subscribe({
      next: () => {
        this.notification.success(
          id ? 'User updated successfully.' : 'User created successfully.'
        );
        this.saving.set(false);
        this.saved.emit();
        this.close();
      },
      // Errors are surfaced by httpErrorInterceptor — just release the button
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
    if (control.hasError('email')) return 'Enter a valid email address.';
    if (control.hasError('minlength')) {
      return `Minimum ${control.getError('minlength').requiredLength} characters.`;
    }
    if (control.hasError('maxlength')) {
      return `Maximum ${control.getError('maxlength').requiredLength} characters.`;
    }
    if (control.hasError('pattern')) {
      return 'Only letters, digits, dot, underscore and hyphen are allowed.';
    }
    return '';
  }
}
