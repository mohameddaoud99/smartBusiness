import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { UserRequest, UserResponse, UserStatus } from '../../features/users/user.model';
import { AuditLogResponse } from '../../features/audit/audit.model';

export interface UserQuery {
  search?: string;
  roleId?: number | null;
  status?: UserStatus | null;
  page: number;
  size: number;
  sortField?: string;
  sortOrder?: number;
}

@Injectable({ providedIn: 'root' })
export class UserService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/users`;

  search(query: UserQuery): Observable<Page<UserResponse>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size);

    if (query.search) {
      params = params.set('search', query.search);
    }
    if (query.roleId) {
      params = params.set('roleId', query.roleId);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.sortField) {
      const direction = query.sortOrder === -1 ? 'desc' : 'asc';
      params = params.set('sort', `${query.sortField},${direction}`);
    }

    return this.http.get<Page<UserResponse>>(this.url, { params });
  }

  findById(id: number): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.url}/${id}`);
  }

  /** The account's own slice of the company audit log. */
  findHistory(id: number): Observable<AuditLogResponse[]> {
    return this.http.get<AuditLogResponse[]>(`${this.url}/${id}/history`);
  }

  create(request: UserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(this.url, request);
  }

  update(id: number, request: UserRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.url}/${id}`, request);
  }

  activate(id: number): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${this.url}/${id}/activate`, {});
  }

  deactivate(id: number): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${this.url}/${id}/deactivate`, {});
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.patch<void>(`${this.url}/${id}/reset-password`, { newPassword });
  }

  // No delete: accounts are deactivated so the history keeps pointing at a real user.
}
