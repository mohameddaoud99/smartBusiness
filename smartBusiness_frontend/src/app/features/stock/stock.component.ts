import { Component, ViewChild, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { MenuModule } from 'primeng/menu';
import { TooltipModule } from 'primeng/tooltip';
import { MenuItem } from 'primeng/api';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { AuthService } from '../../core/auth/auth.service';
import { StockService } from '../../core/services/stock.service';
import { WarehouseService } from '../../core/services/warehouse.service';
import { WarehouseResponse } from '../settings/warehouses/warehouse.model';
import { StockLevel } from './stock.model';
import { StockMovementFormComponent } from './stock-movement-form/stock-movement-form.component';

/** The inventory: what each good has right now, what is promised, and what is left to sell. */
@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [
    DecimalPipe, FormsModule, TableModule, ButtonModule, InputTextModule, IconFieldModule,
    InputIconModule, SelectModule, CheckboxModule, TagModule, MenuModule, TooltipModule,
    PageHeaderComponent, HasPermissionDirective, StockMovementFormComponent
  ],
  templateUrl: './stock.component.html',
  styleUrl: './stock.component.scss'
})
export class StockComponent {

  private readonly stockService = inject(StockService);
  private readonly warehouseService = inject(WarehouseService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  @ViewChild(StockMovementFormComponent) form!: StockMovementFormComponent;

  readonly levels = signal<StockLevel[]>([]);
  readonly warehouses = signal<WarehouseResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  searchTerm = '';
  warehouseFilter: number | null = null;
  lowOnly = false;

  rowMenuItems: MenuItem[] = [];

  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10 };
  private readonly searchInput = new Subject<string>();

  constructor() {
    this.searchInput
      .pipe(debounceTime(350), distinctUntilChanged())
      .subscribe(() => this.reload());
    this.warehouseService.findAll().subscribe(list => this.warehouses.set(list));
  }

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 10;
    this.stockService.levels({
      search: this.searchTerm,
      warehouseId: this.warehouseFilter,
      lowOnly: this.lowOnly,
      page: (event.first ?? 0) / rows,
      size: rows,
      sortField: event.sortField as string,
      sortOrder: event.sortOrder ?? 1
    }).subscribe({
      next: page => {
        this.levels.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSearchChange() {
    this.searchInput.next(this.searchTerm);
  }

  /** Minimum stock is a company-wide figure: it says nothing about one warehouse, so the filter switches off. */
  onWarehouseChange() {
    if (this.warehouseFilter) {
      this.lowOnly = false;
    }
    this.reload();
  }

  onFilterChange() {
    this.reload();
  }

  clearFilters() {
    this.searchTerm = '';
    this.warehouseFilter = null;
    this.lowOnly = false;
    this.reload();
  }

  get hasFilters(): boolean {
    return !!this.searchTerm || !!this.warehouseFilter || this.lowOnly;
  }

  /** Back to page 1 — results change, so the current offset is meaningless. */
  reload() {
    this.load({ ...this.lastEvent, first: 0 });
  }

  refresh() {
    this.load(this.lastEvent);
  }

  openMovements() {
    this.router.navigate(['/stock/movements']);
  }

  openMovement(productId?: number) {
    this.form.open(productId);
  }

  buildRowMenu(level: StockLevel) {
    this.rowMenuItems = [];
    if (this.auth.hasAny(['STOCK_ADJUST', 'STOCK_TRANSFER'])) {
      this.rowMenuItems.push({
        label: 'New movement', icon: 'pi pi-plus', command: () => this.openMovement(level.productId)
      });
    }
    this.rowMenuItems.push({
      label: 'View movements', icon: 'pi pi-list',
      command: () => this.router.navigate(['/stock/movements'], { queryParams: { productId: level.productId } })
    });
  }
}
