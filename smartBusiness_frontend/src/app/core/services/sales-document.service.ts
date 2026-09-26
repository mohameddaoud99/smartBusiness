import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import {
  SalesDocumentRequest, SalesDocumentResponse, SalesDocumentStatus,
  SalesDocumentSummary, SalesDocumentType
} from '../../features/sales/sales-document.model';

export interface SalesDocumentQuery {
  type: SalesDocumentType;
  search?: string;
  status?: SalesDocumentStatus | null;
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: number;
}

@Injectable({ providedIn: 'root' })
export class SalesDocumentService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/sales-documents`;

  search(query: SalesDocumentQuery): Observable<Page<SalesDocumentSummary>> {
    let params = new HttpParams()
      .set('type', query.type)
      .set('page', query.page)
      .set('size', query.size);

    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.sortField) {
      const direction = query.sortOrder === -1 ? 'desc' : 'asc';
      params = params.set('sort', `${query.sortField},${direction}`);
    }

    return this.http.get<Page<SalesDocumentSummary>>(this.url, { params });
  }

  findById(id: number): Observable<SalesDocumentResponse> {
    return this.http.get<SalesDocumentResponse>(`${this.url}/${id}`);
  }

  /** The totals a request would produce — nothing is saved. */
  preview(request: SalesDocumentRequest): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/preview`, request);
  }

  create(request: SalesDocumentRequest): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(this.url, request);
  }

  update(id: number, request: SalesDocumentRequest): Observable<SalesDocumentResponse> {
    return this.http.put<SalesDocumentResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  issue(id: number): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/issue`, {});
  }

  changeStatus(id: number, status: SalesDocumentStatus): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/status`, { status });
  }

  cancel(id: number): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/cancel`, {});
  }

  /** A draft delivery note from a confirmed sales order, to adjust to what really leaves. */
  convertToDeliveryNote(id: number): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/convert-to-delivery-note`, {});
  }

  /** A draft return note from a delivered delivery note or an issued invoice, to lower to what really comes back. */
  convertToReturnNote(id: number): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/convert-to-return-note`, {});
  }

  /** A draft credit note from an issued invoice, to lower to what is really credited. */
  convertToCreditNote(id: number): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/convert-to-credit-note`, {});
  }

  /** A draft invoice from an issued quote, a confirmed order or a delivered delivery note. */
  convertToInvoice(id: number): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/convert-to-invoice`, {});
  }

  convertToOrder(id: number): Observable<SalesDocumentResponse> {
    return this.http.post<SalesDocumentResponse>(`${this.url}/${id}/convert-to-order`, {});
  }
}
