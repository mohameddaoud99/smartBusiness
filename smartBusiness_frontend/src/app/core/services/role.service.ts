import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PermissionModuleGroup, RoleRequest, RoleResponse
} from '../../features/roles/role.model';

@Injectable({ providedIn: 'root' })
export class RoleService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/roles`;

  /** Not paginated: a company has a handful of roles and forms need them all. */
  findAll(): Observable<RoleResponse[]> {
    return this.http.get<RoleResponse[]>(this.url);
  }

  findById(id: number): Observable<RoleResponse> {
    return this.http.get<RoleResponse>(`${this.url}/${id}`);
  }

  /** The permission catalogue the matrix is drawn from. */
  permissionCatalogue(): Observable<PermissionModuleGroup[]> {
    return this.http.get<PermissionModuleGroup[]>(`${this.url}/permissions`);
  }

  create(request: RoleRequest): Observable<RoleResponse> {
    return this.http.post<RoleResponse>(this.url, request);
  }

  update(id: number, request: RoleRequest): Observable<RoleResponse> {
    return this.http.put<RoleResponse>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
