import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { AuditAction, AuditEntity, AuditLogResponse } from '../../features/audit/audit.model';

export interface AuditQuery {
  action?: AuditAction | null;
  entityType?: AuditEntity | null;
  from?: Date | null;
  to?: Date | null;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class AuditService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/audit-logs`;

  search(query: AuditQuery): Observable<Page<AuditLogResponse>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size);

    if (query.action) {
      params = params.set('action', query.action);
    }
    if (query.entityType) {
      params = params.set('entityType', query.entityType);
    }
    if (query.from) {
      params = params.set('from', startOfDay(query.from));
    }
    if (query.to) {
      params = params.set('to', endOfDay(query.to));
    }

    return this.http.get<Page<AuditLogResponse>>(this.url, { params });
  }
}

/** The backend expects a local ISO date-time; a picked day means the whole day. */
function startOfDay(date: Date): string {
  return toLocalIso(new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0));
}

function endOfDay(date: Date): string {
  return toLocalIso(new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59));
}

function toLocalIso(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
