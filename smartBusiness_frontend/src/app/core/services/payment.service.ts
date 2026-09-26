import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { PaymentRequest, PaymentResponse } from '../../features/payments/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/payments`;

  /** The payments of one invoice, newest first — cancelled ones included, they are part of its story. */
  findByInvoice(invoiceId: number): Observable<Page<PaymentResponse>> {
    const params = new HttpParams().set('invoiceId', invoiceId).set('page', 0).set('size', 100);
    return this.http.get<Page<PaymentResponse>>(this.url, { params });
  }

  create(request: PaymentRequest): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(this.url, request);
  }

  /** Payments are never edited or deleted: a wrong one is cancelled. */
  cancel(id: number): Observable<PaymentResponse> {
    return this.http.post<PaymentResponse>(`${this.url}/${id}/cancel`, {});
  }
}
