import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AuditService } from '../../core/services/audit.service';
import {
  ACTION_OPTIONS, AuditAction, AuditEntity, AuditLogResponse,
  ENTITY_OPTIONS, auditColor, auditIcon
} from './audit.model';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [
    FormsModule, DatePipe, TableModule, ButtonModule,
    SelectModule, DatePickerModule, TooltipModule, PageHeaderComponent
  ],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss'
})
export class AuditComponent {

  private readonly auditService = inject(AuditService);

  readonly actionOptions = ACTION_OPTIONS;
  readonly entityOptions = ENTITY_OPTIONS;
  readonly auditIcon = auditIcon;
  readonly auditColor = auditColor;

  readonly logs = signal<AuditLogResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  actionFilter: AuditAction | null = null;
  entityFilter: AuditEntity | null = null;
  fromFilter: Date | null = null;
  toFilter: Date | null = null;

  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 25 };

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 25;
    this.auditService.search({
      action: this.actionFilter,
      entityType: this.entityFilter,
      from: this.fromFilter,
      to: this.toFilter,
      page: (event.first ?? 0) / rows,
      size: rows
    }).subscribe({
      next: page => {
        this.logs.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  /** Filters change the result set, so the current offset is meaningless. */
  onFilterChange() {
    this.load({ ...this.lastEvent, first: 0 });
  }

  clearFilters() {
    this.actionFilter = null;
    this.entityFilter = null;
    this.fromFilter = null;
    this.toFilter = null;
    this.onFilterChange();
  }

  get hasFilters(): boolean {
    return !!(this.actionFilter || this.entityFilter || this.fromFilter || this.toFilter);
  }
}
