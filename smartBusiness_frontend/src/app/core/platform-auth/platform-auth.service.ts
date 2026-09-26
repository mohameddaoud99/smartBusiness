import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PlatformAuthResponse, PlatformLoginRequest, PlatformSession } from './platform-session.model';

/**
 * A deliberately different storage key from the company session ({@code smartbusiness.token}):
 * a platform admin and a company user must never be able to clobber each other's session
 * in the same browser.
 */
const TOKEN_KEY = 'smartbusiness.platform.token';

@Injectable({ providedIn: 'root' })
export class PlatformAuthService {

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly url = `${environment.apiUrl}/platform/auth`;

  private readonly currentAdmin = signal<PlatformSession | null>(null);

  readonly session = this.currentAdmin.asReadonly();
  readonly isAuthenticated = computed(() => this.currentAdmin() !== null);

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  login(request: PlatformLoginRequest): Observable<PlatformAuthResponse> {
    return this.http.post<PlatformAuthResponse>(`${this.url}/login`, request)
      .pipe(tap(response => this.start(response)));
  }

  restore(): Observable<PlatformSession | null> {
    if (!this.token) {
      return of(null);
    }

    return this.http.get<PlatformSession>(`${this.url}/me`).pipe(
      tap(admin => this.currentAdmin.set(admin)),
      catchError(() => {
        this.clear();
        return of(null);
      })
    );
  }

  logout(): void {
    this.clear();
    this.router.navigate(['/platform/login']);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.currentAdmin.set(null);
  }

  private start(response: PlatformAuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.token);
    this.currentAdmin.set(response.admin);
  }
}
