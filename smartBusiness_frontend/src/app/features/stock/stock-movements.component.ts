import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { StockService } from '../../core/services/stock.service';
import { ProductService } from '../../core/services/product.service';
import { WarehouseService } from '../../core/services/warehouse.service';
import { ProductResponse } from '../products/product.model';
import { WarehouseResponse } from '../settings/warehouses/warehouse.model';
import {
  MOVEMENT_TYPE_OPTIONS, StockMovement, StockMovementType, movementLabel, movementSeverity
} from './stock.model';

/** The append-only register behind every level: who moved what, where, and why. */
@Component({
  selector: 'app-stock-movements',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, RouterLink, FormsModule, TableModule, ButtonModule, SelectModule,
    TagModule, TooltipModule, PageHeaderComponent
  ],
  templateUrl: './stock-movements.component.html',
  styleUrl: './stock-movements.component.scss'
})
export class StockMovementsComponent {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly stockService = inject(StockService);
  private readonly productService = inject(ProductService);
  private readonly warehouseService = inject(WarehouseService);

  readonly typeOptions = MOVEMENT_TYPE_OPTIONS;
  readonly movementLabel = movementLabel;
  readonly movementSeverity = movementSeverity;

  readonly movements = signal<StockMovement[]>([]);
  readonly products = signal<ProductResponse[]>([]);
  readonly warehouses = signal<WarehouseResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  /** Coming from a row of the inventory pre-selects its product. */
  productFilter: number | null = Number(this.route.snapshot.queryParamMap.get('productId')) || null;
  warehouseFilter: number | null = null;
  typeFilter: StockMovementType | null = null;

  private lastEvent: TableLazyLoadEvent = { first: 0, rows: 10 };

  constructor() {
    this.productService.search({ kind: 'GOOD', page: 0, size: 500, sortField: 'name', sortOrder: 1 })
      .subscribe(page => this.products.set(page.content));
    this.warehouseService.findAll().subscribe(list => this.warehouses.set(list));
  }

  load(event: TableLazyLoadEvent) {
    this.lastEvent = event;
    this.loading.set(true);

    const rows = event.rows ?? 10;
    this.stockService.movements({
      productId: this.productFilter,
      warehouseId: this.warehouseFilter,
      type: this.typeFilter,
      page: (event.first ?? 0) / rows,
      size: rows,
      sortField: event.sortField as string,
      sortOrder: event.sortOrder ?? 1
    }).subscribe({
      next: page => {
        this.movements.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onFilterChange() {
    this.reload();
  }

  clearFilters() {
    this.productFilter = null;
    this.warehouseFilter = null;
    this.typeFilter = null;
    this.reload();
  }

  get hasFilters(): boolean {
    return !!this.productFilter || !!this.warehouseFilter || !!this.typeFilter;
  }

  /** Back to page 1 — results change, so the current offset is meaningless. */
  reload() {
    this.load({ ...this.lastEvent, first: 0 });
  }

  openStock() {
    this.router.navigate(['/stock']);
  }
}
