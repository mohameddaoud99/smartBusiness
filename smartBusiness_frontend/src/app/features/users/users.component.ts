import { Component, inject, signal, ViewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { MenuModule } from 'primeng/menu';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { UserService } from '../../core/services/user.service';
import { RoleService } from '../../core/services/role.service';
import { NotificationService } from '../../core/services/notification.service';
import { RoleResponse } from '../roles/role.model';
import { UserFormComponent } from './user-form/user-form.component';
import { UserDetailComponent } from './user-detail/user-detail.component';
import {
  UserResponse, UserStatus, STATUS_OPTIONS, statusLabel, statusSeverity
} from './user.model';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    DatePipe, FormsModule, TableModule, ButtonModule, InputTextModule,
    IconFieldModule, InputIconModule, SelectModule, TagModule, MenuModule,
    TooltipModule, DialogModule, PageHeaderComponent, HasPermissionDirective,
    UserFormComponent, UserDetailComponent
  ],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class UsersComponent {

  private readonly userService = inject(UserService);
  private readonly roleService = inject(RoleService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(UserFormComponent) form!: UserFormComponent;
  @ViewChild(UserDetailComponent) detail!: UserDetailComponent;

  readonly statusOptions = STATUS_OPTIONS;
  readonly statusLabel = statusLabel;
  readonly statusSeverity = statusSeverity;

  readonly roles = signal<RoleResponse[]>([]);
  readonly users = signal<UserResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  searchTerm = '';
  roleFilter: number | null = null;
  statusFilter: UserStatus | null = null;

  /** Row menu is built for whichever row was clicked. */
  rowMenuItems: MenuItem[] = [];

  // Reset-password dialog
  readonly resetVisible = signal(false);
  readonly resetSaving = signal(false);
  resetTarget: UserResponse | null = null;
  newPassword = '';

  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10 };
  private readonly searchInput = new Subject<string>();

  constructor() {
    // Wait for the user to stop typing before hitting the API
    this.searchInput
      .pipe(debounceTime(350), distinctUntilChanged())
      .subscribe(() => this.reload());

    // Feeds the role filter — the company's own roles, not a hard-coded list
    this.roleService.findAll().subscribe(roles => this.roles.set(roles));
  }

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 10;
    this.userService.search({
      search: this.searchTerm,
      roleId: this.roleFilter,
      status: this.statusFilter,
      page: (event.first ?? 0) / rows,
      size: rows,
      sortField: event.sortField as string,
      sortOrder: event.sortOrder ?? 1
    }).subscribe({
      next: page => {
        this.users.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSearchChange() {
    this.searchInput.next(this.searchTerm);
  }

  onFilterChange() {
    this.reload();
  }

  clearFilters() {
    this.searchTerm = '';
    this.roleFilter = null;
    this.statusFilter = null;
    this.reload();
  }

  get hasFilters(): boolean {
    return !!this.searchTerm || !!this.roleFilter || !!this.statusFilter;
  }

  /** Back to page 1 — results change, so the current offset is meaningless. */
  reload() {
    this.load({ ...this.lastEvent, first: 0 });
  }

  refresh() {
    this.load(this.lastEvent);
  }

  // ----- Actions -----

  openCreate() {
    this.form.open();
  }

  openEdit(user: UserResponse) {
    this.form.open(user);
  }

  openDetail(user: UserResponse) {
    this.detail.open(user);
  }

  buildRowMenu(user: UserResponse) {
    this.rowMenuItems = [
      { label: 'View details', icon: 'pi pi-eye', command: () => this.openDetail(user) },
      { label: 'Edit', icon: 'pi pi-pencil', command: () => this.openEdit(user) },
      { separator: true },
      user.status === 'ACTIVE'
        ? { label: 'Deactivate', icon: 'pi pi-ban', command: () => this.confirmDeactivate(user) }
        : { label: 'Activate', icon: 'pi pi-check', command: () => this.confirmActivate(user) },
      { label: 'Reset password', icon: 'pi pi-lock', command: () => this.openReset(user) }
    ];
  }

  confirmDeactivate(user: UserResponse) {
    this.confirmation.confirm({
      header: 'Deactivate user',
      message: `${user.fullName} will no longer be able to sign in. You can reactivate the account at any time.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Deactivate',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.userService.deactivate(user.id).subscribe(() => {
          this.notification.success(`${user.fullName} has been deactivated.`);
          this.refresh();
        });
      }
    });
  }

  confirmActivate(user: UserResponse) {
    this.confirmation.confirm({
      header: 'Activate user',
      message: `${user.fullName} will be able to sign in again.`,
      icon: 'pi pi-check-circle',
      acceptLabel: 'Activate',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.userService.activate(user.id).subscribe(() => {
          this.notification.success(`${user.fullName} has been activated.`);
          this.refresh();
        });
      }
    });
  }

  openReset(user: UserResponse) {
    this.resetTarget = user;
    this.newPassword = '';
    this.resetVisible.set(true);
  }

  submitReset() {
    if (!this.resetTarget || this.newPassword.length < 8) {
      return;
    }
    this.resetSaving.set(true);
    this.userService.resetPassword(this.resetTarget.id, this.newPassword).subscribe({
      next: () => {
        this.notification.success(`Password reset for ${this.resetTarget!.fullName}.`);
        this.resetSaving.set(false);
        this.resetVisible.set(false);
      },
      error: () => this.resetSaving.set(false)
    });
  }

  initials(user: UserResponse): string {
    return (user.firstName.charAt(0) + user.lastName.charAt(0)).toUpperCase();
  }
}
