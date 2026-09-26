import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CompanyRequest, CompanyResponse } from '../../features/settings/company/company.model';

@Injectable({ providedIn: 'root' })
export class CompanyService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/company`;

  find(): Observable<CompanyResponse> {
    return this.http.get<CompanyResponse>(this.url);
  }

  update(request: CompanyRequest): Observable<CompanyResponse> {
    return this.http.put<CompanyResponse>(this.url, request);
  }

  uploadLogo(file: File): Observable<CompanyResponse> {
    return this.http.post<CompanyResponse>(`${this.url}/logo`, toFormData(file));
  }

  removeLogo(): Observable<CompanyResponse> {
    return this.http.delete<CompanyResponse>(`${this.url}/logo`);
  }

  uploadStamp(file: File): Observable<CompanyResponse> {
    return this.http.post<CompanyResponse>(`${this.url}/stamp`, toFormData(file));
  }

  removeStamp(): Observable<CompanyResponse> {
    return this.http.delete<CompanyResponse>(`${this.url}/stamp`);
  }
}

function toFormData(file: File): FormData {
  const formData = new FormData();
  formData.append('file', file);
  return formData;
}
