import { Component, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { TooltipModule } from 'primeng/tooltip';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PlatformCompanyService } from '../../../core/services/platform-company.service';
import { MODULE_OPTIONS, PlatformCompanyResponse } from './platform-company.model';
import { CompanyModulesDialogComponent } from './company-modules-dialog/company-modules-dialog.component';

@Component({
  selector: 'app-platform-companies',
  standalone: true,
  imports: [
    FormsModule, DatePipe, TableModule, ButtonModule, TagModule,
    InputTextModule, IconFieldModule, InputIconModule, TooltipModule,
    PageHeaderComponent, CompanyModulesDialogComponent
  ],
  templateUrl: './platform-companies.component.html',
  styleUrl: './platform-companies.component.scss'
})
export class PlatformCompaniesComponent {

  private readonly companyService = inject(PlatformCompanyService);

  @ViewChild(CompanyModulesDialogComponent) modulesDialog!: CompanyModulesDialogComponent;

  readonly moduleOptions = MODULE_OPTIONS;

  readonly companies = signal<PlatformCompanyResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  searchTerm = '';
  private readonly searchInput = new Subject<void>();
  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 20 };

  constructor() {
    this.searchInput
      .pipe(debounceTime(350), distinctUntilChanged())
      .subscribe(() => this.load({ ...this.lastEvent, first: 0 }));
  }

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 20;
    this.companyService.search(this.searchTerm, (event.first ?? 0) / rows, rows).subscribe({
      next: page => {
        this.companies.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSearchChange() {
    this.searchInput.next();
  }

  refresh() {
    this.load(this.lastEvent);
  }

  /** "Sales, Stock +2" — enough to recognise the plan at a glance. */
  moduleSummary(company: PlatformCompanyResponse): string {
    if (company.enabledModules.length === 0) {
      return 'None';
    }
    const labels = company.enabledModules
      .map(value => this.moduleOptions.find(option => option.value === value)?.label ?? value);

    return labels.length <= 3
      ? labels.join(', ')
      : `${labels.slice(0, 3).join(', ')} +${labels.length - 3}`;
  }

  manageModules(company: PlatformCompanyResponse) {
    this.modulesDialog.open(company);
  }
}
