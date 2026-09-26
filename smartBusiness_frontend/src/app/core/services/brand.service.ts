import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { BrandRequest, BrandResponse } from '../../features/settings/brands/brand.model';

@Injectable({ providedIn: 'root' })
export class BrandService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/brands`;

  findAll(): Observable<BrandResponse[]> {
    return this.http.get<BrandResponse[]>(this.url);
  }

  create(request: BrandRequest): Observable<BrandResponse> {
    return this.http.post<BrandResponse>(this.url, request);
  }

  update(id: number, request: BrandRequest): Observable<BrandResponse> {
    return this.http.put<BrandResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
