import { Component, computed, input, model } from '@angular/core';
import { CheckboxModule } from 'primeng/checkbox';
import { FormsModule } from '@angular/forms';

import { Permission } from '../../../core/auth/session.model';
import { PermissionModuleGroup } from '../role.model';

/**
 * The permission matrix: one row per module, one checkbox per action.
 *
 * It renders whatever the backend catalogue contains, so a new business module shows up
 * here on its own — no change to this component.
 */
@Component({
  selector: 'app-permission-matrix',
  standalone: true,
  imports: [FormsModule, CheckboxModule],
  templateUrl: './permission-matrix.component.html',
  styleUrl: './permission-matrix.component.scss'
})
export class PermissionMatrixComponent {

  readonly groups = input.required<PermissionModuleGroup[]>();

  /** System roles are shown but cannot be edited. */
  readonly readonly = input(false);

  readonly selected = model<Permission[]>([]);

  readonly selectedCount = computed(() => this.selected().length);

  readonly totalCount = computed(() =>
    this.groups().reduce((total, group) => total + group.permissions.length, 0)
  );

  isChecked(permission: Permission): boolean {
    return this.selected().includes(permission);
  }

  toggle(permission: Permission) {
    if (this.readonly()) {
      return;
    }
    this.selected.update(current =>
      current.includes(permission)
        ? current.filter(item => item !== permission)
        : [...current, permission]
    );
  }

  /** A module is fully granted when every one of its permissions is ticked. */
  isModuleFull(group: PermissionModuleGroup): boolean {
    return group.permissions.every(permission => this.isChecked(permission.name));
  }

  isModulePartial(group: PermissionModuleGroup): boolean {
    return !this.isModuleFull(group)
      && group.permissions.some(permission => this.isChecked(permission.name));
  }

  toggleModule(group: PermissionModuleGroup) {
    if (this.readonly()) {
      return;
    }
    const names = group.permissions.map(permission => permission.name);
    const full = this.isModuleFull(group);

    this.selected.update(current => full
      ? current.filter(item => !names.includes(item))
      : [...new Set([...current, ...names])]
    );
  }

  selectAll() {
    if (this.readonly()) {
      return;
    }
    this.selected.set(this.groups().flatMap(group => group.permissions.map(p => p.name)));
  }

  clearAll() {
    if (this.readonly()) {
      return;
    }
    this.selected.set([]);
  }
}
