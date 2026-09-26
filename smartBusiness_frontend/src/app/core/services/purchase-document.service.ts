import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import {
  PurchaseDocumentRequest, PurchaseDocumentResponse, PurchaseDocumentStatus,
  PurchaseDocumentSummary, PurchaseDocumentType
} from '../../features/purchases/purchase-document.model';

export interface PurchaseDocumentQuery {
  type: PurchaseDocumentType;
  search?: string;
  status?: PurchaseDocumentStatus | null;
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: number;
}

@Injectable({ providedIn: 'root' })
export class PurchaseDocumentService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/purchase-documents`;

  search(query: PurchaseDocumentQuery): Observable<Page<PurchaseDocumentSummary>> {
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

    return this.http.get<Page<PurchaseDocumentSummary>>(this.url, { params });
  }

  findById(id: number): Observable<PurchaseDocumentResponse> {
    return this.http.get<PurchaseDocumentResponse>(`${this.url}/${id}`);
  }

  /** The totals a request would produce — nothing is saved. */
  preview(request: PurchaseDocumentRequest): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(`${this.url}/preview`, request);
  }

  create(request: PurchaseDocumentRequest): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(this.url, request);
  }

  update(id: number, request: PurchaseDocumentRequest): Observable<PurchaseDocumentResponse> {
    return this.http.put<PurchaseDocumentResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  /** Numbers and freezes a draft; for a goods receipt, also puts its goods into the stock. */
  validate(id: number): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(`${this.url}/${id}/validate`, {});
  }

  cancel(id: number): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(`${this.url}/${id}/cancel`, {});
  }

  /** A draft return note from a validated goods receipt or invoice, to lower to what really goes back. */
  convertToReturnNote(id: number): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(`${this.url}/${id}/convert-to-return-note`, {});
  }

  /** A draft supplier credit note from a validated invoice, to lower to what is really credited. */
  convertToCreditNote(id: number): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(`${this.url}/${id}/convert-to-credit-note`, {});
  }

  /** A draft invoice from a validated purchase order or goods receipt. */
  convertToInvoice(id: number): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(`${this.url}/${id}/convert-to-invoice`, {});
  }

  convertToReceipt(id: number): Observable<PurchaseDocumentResponse> {
    return this.http.post<PurchaseDocumentResponse>(`${this.url}/${id}/convert-to-receipt`, {});
  }
}
