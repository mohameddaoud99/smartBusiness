import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PurchaseFigures, SalesFigures, StockFigures } from '../../features/dashboard/dashboard.model';

/** One call per business area: each is refused (403) to someone without the right to that module. */
@Injectable({ providedIn: 'root' })
export class DashboardService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/dashboard`;

  sales(): Observable<SalesFigures> {
    return this.http.get<SalesFigures>(`${this.url}/sales`);
  }

  purchases(): Observable<PurchaseFigures> {
    return this.http.get<PurchaseFigures>(`${this.url}/purchases`);
  }

  stock(): Observable<StockFigures> {
    return this.http.get<StockFigures>(`${this.url}/stock`);
  }
}
