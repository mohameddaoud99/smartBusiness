import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import {
  StockLevel, StockMovement, StockMovementRequest, StockMovementType, StockTransferRequest
} from '../../features/stock/stock.model';

export interface StockLevelQuery {
  search?: string;
  warehouseId?: number | null;
  lowOnly?: boolean;
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: number;
}

export interface StockMovementQuery {
  productId?: number | null;
  warehouseId?: number | null;
  type?: StockMovementType | null;
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: number;
}

@Injectable({ providedIn: 'root' })
export class StockService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/stock`;

  levels(query: StockLevelQuery): Observable<Page<StockLevel>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.warehouseId) {
      params = params.set('warehouseId', query.warehouseId);
    }
    if (query.lowOnly) {
      params = params.set('lowOnly', true);
    }
    return this.http.get<Page<StockLevel>>(`${this.url}/levels`, { params: this.sorted(params, query) });
  }

  movements(query: StockMovementQuery): Observable<Page<StockMovement>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);

    if (query.productId) {
      params = params.set('productId', query.productId);
    }
    if (query.warehouseId) {
      params = params.set('warehouseId', query.warehouseId);
    }
    if (query.type) {
      params = params.set('type', query.type);
    }
    return this.http.get<Page<StockMovement>>(`${this.url}/movements`, { params: this.sorted(params, query) });
  }

  record(request: StockMovementRequest): Observable<StockMovement> {
    return this.http.post<StockMovement>(`${this.url}/movements`, request);
  }

  transfer(request: StockTransferRequest): Observable<StockMovement[]> {
    return this.http.post<StockMovement[]>(`${this.url}/transfers`, request);
  }

  private sorted(params: HttpParams, query: { sortField?: string; sortOrder?: number }): HttpParams {
    if (!query.sortField) {
      return params;
    }
    return params.set('sort', `${query.sortField},${query.sortOrder === -1 ? 'desc' : 'asc'}`);
  }
}
