import { Component, ViewChild, inject, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { RoleService } from '../../core/services/role.service';
import { NotificationService } from '../../core/services/notification.service';
import { RoleResponse } from './role.model';
import { RoleFormComponent } from './role-form/role-form.component';

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [
    TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, RoleFormComponent
  ],
  templateUrl: './roles.component.html',
  styleUrl: './roles.component.scss'
})
export class RolesComponent {

  private readonly roleService = inject(RoleService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(RoleFormComponent) form!: RoleFormComponent;

  readonly roles = signal<RoleResponse[]>([]);
  readonly loading = signal(false);

  constructor() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.roleService.findAll().subscribe({
      next: roles => {
        this.roles.set(roles);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate() {
    this.form.open();
  }

  openEdit(role: RoleResponse) {
    this.form.open(role);
  }

  confirmDelete(role: RoleResponse) {
    this.confirmation.confirm({
      header: 'Delete role',
      message: `"${role.label}" will be removed permanently. This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Delete',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.roleService.delete(role.id).subscribe(() => {
          this.notification.success(`"${role.label}" has been deleted.`);
          this.load();
        });
      }
    });
  }

  /** The two or three modules a role touches — enough to recognise it at a glance. */
  moduleSummary(role: RoleResponse): string {
    const modules = [...new Set(role.permissions.map(this.moduleOf))];
    return modules.length <= 3
      ? modules.join(', ')
      : `${modules.slice(0, 3).join(', ')} +${modules.length - 3}`;
  }

  private moduleOf(permission: string): string {
    const prefix = permission.split('_')[0];
    return prefix.charAt(0) + prefix.slice(1).toLowerCase();
  }
}
