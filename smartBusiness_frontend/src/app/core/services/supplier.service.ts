import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { SupplierRequest, SupplierResponse, SupplierType } from '../../features/suppliers/supplier.model';

export interface SupplierQuery {
  search?: string;
  type?: SupplierType | null;
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: number;
}

@Injectable({ providedIn: 'root' })
export class SupplierService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/suppliers`;

  search(query: SupplierQuery): Observable<Page<SupplierResponse>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size);

    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.type) {
      params = params.set('type', query.type);
    }
    if (query.sortField) {
      const direction = query.sortOrder === -1 ? 'desc' : 'asc';
      params = params.set('sort', `${query.sortField},${direction}`);
    }

    return this.http.get<Page<SupplierResponse>>(this.url, { params });
  }

  findById(id: number): Observable<SupplierResponse> {
    return this.http.get<SupplierResponse>(`${this.url}/${id}`);
  }

  create(request: SupplierRequest): Observable<SupplierResponse> {
    return this.http.post<SupplierResponse>(this.url, request);
  }

  update(id: number, request: SupplierRequest): Observable<SupplierResponse> {
    return this.http.put<SupplierResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
