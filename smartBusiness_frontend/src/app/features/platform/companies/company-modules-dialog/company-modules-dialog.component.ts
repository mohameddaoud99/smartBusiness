import { Component, EventEmitter, Output, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';

import { PlatformCompanyService } from '../../../../core/services/platform-company.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { BusinessModule, MODULE_OPTIONS, PlatformCompanyResponse } from '../platform-company.model';

/**
 * Which business modules a single company may use. Deliberately plain checkboxes,
 * not a matrix: there are only six of them, and this is a high-stakes admin action —
 * the state should be readable at a glance, not compressed into chips.
 */
@Component({
  selector: 'app-company-modules-dialog',
  standalone: true,
  imports: [FormsModule, DialogModule, ButtonModule, CheckboxModule],
  templateUrl: './company-modules-dialog.component.html',
  styleUrl: './company-modules-dialog.component.scss'
})
export class CompanyModulesDialogComponent {

  private readonly companyService = inject(PlatformCompanyService);
  private readonly notification = inject(NotificationService);

  @Output() saved = new EventEmitter<void>();

  readonly moduleOptions = MODULE_OPTIONS;

  readonly visible = signal(false);
  readonly saving = signal(false);
  readonly company = signal<PlatformCompanyResponse | null>(null);
  readonly selected = signal<BusinessModule[]>([]);

  readonly title = computed(() => this.company() ? `Modules — ${this.company()!.name}` : 'Modules');

  open(company: PlatformCompanyResponse) {
    this.company.set(company);
    this.selected.set([...company.enabledModules]);
    this.visible.set(true);
  }

  close() {
    this.visible.set(false);
  }

  isChecked(module: BusinessModule): boolean {
    return this.selected().includes(module);
  }

  toggle(module: BusinessModule) {
    this.selected.update(current =>
      current.includes(module) ? current.filter(m => m !== module) : [...current, module]
    );
  }

  submit() {
    const company = this.company();
    if (!company) {
      return;
    }

    this.saving.set(true);
    this.companyService.updateModules(company.id, { modules: this.selected() }).subscribe({
      next: () => {
        this.notification.success(`Modules updated for ${company.name}.`);
        this.saving.set(false);
        this.saved.emit();
        this.close();
      },
      error: () => this.saving.set(false)
    });
  }
}
