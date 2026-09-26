import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import {
  PlatformCompanyResponse, UpdateCompanyModulesRequest
} from '../../features/platform/companies/platform-company.model';

@Injectable({ providedIn: 'root' })
export class PlatformCompanyService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/platform/companies`;

  search(search: string, page: number, size: number): Observable<Page<PlatformCompanyResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<Page<PlatformCompanyResponse>>(this.url, { params });
  }

  updateModules(id: number, request: UpdateCompanyModulesRequest): Observable<PlatformCompanyResponse> {
    return this.http.put<PlatformCompanyResponse>(`${this.url}/${id}/modules`, request);
  }
}
