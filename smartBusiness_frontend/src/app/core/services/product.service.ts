import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { ProductKind, ProductRequest, ProductResponse } from '../../features/products/product.model';

export interface ProductQuery {
  search?: string;
  kind?: ProductKind | null;
  categoryId?: number | null;
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: number;
}

@Injectable({ providedIn: 'root' })
export class ProductService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/products`;

  search(query: ProductQuery): Observable<Page<ProductResponse>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size);

    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.kind) {
      params = params.set('kind', query.kind);
    }
    if (query.categoryId) {
      params = params.set('categoryId', query.categoryId);
    }
    if (query.sortField) {
      const direction = query.sortOrder === -1 ? 'desc' : 'asc';
      params = params.set('sort', `${query.sortField},${direction}`);
    }

    return this.http.get<Page<ProductResponse>>(this.url, { params });
  }

  findById(id: number): Observable<ProductResponse> {
    return this.http.get<ProductResponse>(`${this.url}/${id}`);
  }

  create(request: ProductRequest): Observable<ProductResponse> {
    return this.http.post<ProductResponse>(this.url, request);
  }

  update(id: number, request: ProductRequest): Observable<ProductResponse> {
    return this.http.put<ProductResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  addImage(id: number, file: File): Observable<ProductResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ProductResponse>(`${this.url}/${id}/images`, formData);
  }

  removeImage(id: number, imageId: number): Observable<ProductResponse> {
    return this.http.delete<ProductResponse>(`${this.url}/${id}/images/${imageId}`);
  }
}
