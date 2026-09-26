import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import { Page } from '../models/page.model';
import { BranchRequest, BranchResponse } from '../../features/branches/branch.model';

/** Companies have a handful of branches — one page is always enough for a picker. */
const ALL_BRANCHES_PAGE_SIZE = 200;

@Injectable({ providedIn: 'root' })
export class BranchService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/branches`;

  findAll(page: number, size: number): Observable<Page<BranchResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<BranchResponse>>(this.url, { params });
  }

  /** Unpaginated list for dropdowns, e.g. the branch picker on the user form. */
  findAllForPicker(): Observable<BranchResponse[]> {
    return this.findAll(0, ALL_BRANCHES_PAGE_SIZE).pipe(map(page => page.content));
  }

  create(request: BranchRequest): Observable<BranchResponse> {
    return this.http.post<BranchResponse>(this.url, request);
  }

  update(id: number, request: BranchRequest): Observable<BranchResponse> {
    return this.http.put<BranchResponse>(`${this.url}/${id}`, request);
  }

  activate(id: number): Observable<BranchResponse> {
    return this.http.patch<BranchResponse>(`${this.url}/${id}/activate`, {});
  }

  deactivate(id: number): Observable<BranchResponse> {
    return this.http.patch<BranchResponse>(`${this.url}/${id}/deactivate`, {});
  }
}
