import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CompanyBankAccountRequest, CompanyBankAccountResponse
} from '../../features/settings/bank-accounts/bank-account.model';

@Injectable({ providedIn: 'root' })
export class BankAccountService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/settings/bank-accounts`;

  findAll(): Observable<CompanyBankAccountResponse[]> {
    return this.http.get<CompanyBankAccountResponse[]>(this.url);
  }

  create(request: CompanyBankAccountRequest): Observable<CompanyBankAccountResponse> {
    return this.http.post<CompanyBankAccountResponse>(this.url, request);
  }

  update(id: number, request: CompanyBankAccountRequest): Observable<CompanyBankAccountResponse> {
    return this.http.put<CompanyBankAccountResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
