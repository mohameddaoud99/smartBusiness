import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TaxRequest, TaxResponse } from '../../features/settings/taxes/tax.model';

@Injectable({ providedIn: 'root' })
export class TaxService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/settings/taxes`;

  findAll(): Observable<TaxResponse[]> {
    return this.http.get<TaxResponse[]>(this.url);
  }

  create(request: TaxRequest): Observable<TaxResponse> {
    return this.http.post<TaxResponse>(this.url, request);
  }

  update(id: number, request: TaxRequest): Observable<TaxResponse> {
    return this.http.put<TaxResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
