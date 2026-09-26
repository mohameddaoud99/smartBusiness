import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';

import { RoleService } from '../../../core/services/role.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Permission } from '../../../core/auth/session.model';
import { PermissionModuleGroup, RoleResponse } from '../role.model';
import { PermissionMatrixComponent } from '../permission-matrix/permission-matrix.component';

@Component({
  selector: 'app-role-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule,
    TextareaModule, MessageModule, PermissionMatrixComponent
  ],
  templateUrl: './role-form.component.html',
  styleUrl: './role-form.component.scss'
})
export class RoleFormComponent {

  private readonly fb = inject(FormBuilder);
  private readonly roleService = inject(RoleService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly editing = signal<RoleResponse | null>(null);
  readonly catalogue = signal<PermissionModuleGroup[]>([]);

  /**
   * Permissions live outside the reactive form: the matrix owns a plain array, which is
   * simpler to read than wiring a control value accessor for a single screen.
   */
  readonly permissions = signal<Permission[]>([]);
  readonly showPermissionError = signal(false);

  /** A standard role opens in read-only mode: visible, explained, but not editable. */
  readonly locked = computed(() => this.editing()?.system ?? false);

  readonly form: FormGroup = this.fb.group({
    label: ['', [Validators.required, Validators.maxLength(80)]],
    description: ['', Validators.maxLength(255)]
  });

  constructor() {
    this.roleService.permissionCatalogue().subscribe(groups => this.catalogue.set(groups));
  }

  open(role?: RoleResponse) {
    this.form.reset({ label: '', description: '' });
    this.showPermissionError.set(false);

    if (role) {
      this.editing.set(role);
      this.form.patchValue({ label: role.label, description: role.description ?? '' });
      this.permissions.set([...role.permissions]);
    } else {
      this.editing.set(null);
      this.permissions.set([]);
    }

    if (this.locked()) {
      this.form.disable();
    } else {
      this.form.enable();
    }

    this.visible.set(true);
  }

  close() {
    this.visible.set(false);
  }

  submit() {
    const noPermission = this.permissions().length === 0;
    this.showPermissionError.set(noPermission);

    if (this.form.invalid || noPermission) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const role = this.editing();
    const request = {
      label: this.form.value.label,
      description: this.form.value.description || undefined,
      permissions: this.permissions()
    };

    const request$ = role
      ? this.roleService.update(role.id, request)
      : this.roleService.create(request);

    request$.subscribe({
      next: () => {
        this.notification.success(role ? 'Role updated successfully.' : 'Role created successfully.');
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
}
