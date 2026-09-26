import { Component, ViewChild, inject, signal } from '@angular/core';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { BranchService } from '../../core/services/branch.service';
import { NotificationService } from '../../core/services/notification.service';
import { BranchResponse } from './branch.model';
import { BranchFormComponent } from './branch-form/branch-form.component';

@Component({
  selector: 'app-branches',
  standalone: true,
  imports: [
    TableModule, ButtonModule, TagModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, BranchFormComponent
  ],
  templateUrl: './branches.component.html',
  styleUrl: './branches.component.scss'
})
export class BranchesComponent {

  private readonly branchService = inject(BranchService);
  private readonly notification = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  @ViewChild(BranchFormComponent) form!: BranchFormComponent;

  readonly branches = signal<BranchResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10 };

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 10;
    this.branchService.findAll((event.first ?? 0) / rows, rows).subscribe({
      next: page => {
        this.branches.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  refresh() {
    this.load(this.lastEvent);
  }

  openCreate() {
    this.form.open();
  }

  openEdit(branch: BranchResponse) {
    this.form.open(branch);
  }

  confirmDeactivate(branch: BranchResponse) {
    this.confirmation.confirm({
      header: 'Deactivate branch',
      message: `${branch.name} will no longer be available for new operations. You can reactivate it at any time.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Deactivate',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger p-button-sm',
      rejectButtonStyleClass: 'p-button-outlined p-button-secondary p-button-sm',
      accept: () => {
        this.branchService.deactivate(branch.id).subscribe(() => {
          this.notification.success(`${branch.name} has been deactivated.`);
          this.refresh();
        });
      }
    });
  }

  activate(branch: BranchResponse) {
    this.branchService.activate(branch.id).subscribe(() => {
      this.notification.success(`${branch.name} has been activated.`);
      this.refresh();
    });
  }
}
