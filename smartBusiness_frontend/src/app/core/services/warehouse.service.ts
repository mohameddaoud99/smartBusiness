import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { WarehouseRequest, WarehouseResponse } from '../../features/settings/warehouses/warehouse.model';

@Injectable({ providedIn: 'root' })
export class WarehouseService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/warehouses`;

  findAll(): Observable<WarehouseResponse[]> {
    return this.http.get<WarehouseResponse[]>(this.url);
  }

  create(request: WarehouseRequest): Observable<WarehouseResponse> {
    return this.http.post<WarehouseResponse>(this.url, request);
  }

  update(id: number, request: WarehouseRequest): Observable<WarehouseResponse> {
    return this.http.put<WarehouseResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
