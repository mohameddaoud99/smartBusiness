import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AuthResponse, BusinessModule, ChangePasswordRequest, LoginRequest, Permission,
  RegisterRequest, SessionUser, UpdateProfileRequest
} from './session.model';

const TOKEN_KEY = 'smartbusiness.token';

/**
 * Holds the signed-in session. The permission list it exposes drives the menu and the
 * buttons, but it is only a convenience: the backend re-checks every call, so a user
 * who forges this state gains nothing.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly url = `${environment.apiUrl}/auth`;

  private readonly currentUser = signal<SessionUser | null>(null);

  readonly session = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.url}/login`, request)
      .pipe(tap(response => this.start(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.url}/register`, request)
      .pipe(tap(response => this.start(response)));
  }

  /**
   * Runs once at startup. A stored token is only trusted after the backend confirms it,
   * which also refreshes the permissions in case roles changed since the last visit.
   */
  restore(): Observable<SessionUser | null> {
    if (!this.token) {
      return of(null);
    }

    return this.http.get<SessionUser>(`${this.url}/me`).pipe(
      tap(user => this.currentUser.set(user)),
      catchError(() => {
        this.clear();
        return of(null);
      })
    );
  }

  /** Updates the signed-in user's own details and refreshes the held session. */
  updateProfile(request: UpdateProfileRequest): Observable<SessionUser> {
    return this.http.patch<SessionUser>(`${this.url}/me`, request)
      .pipe(tap(user => this.currentUser.set(user)));
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.patch<void>(`${this.url}/change-password`, request);
  }

  logout(): void {
    this.clear();
    this.router.navigate(['/login']);
  }

  /** Clears the session without navigating — used when a request comes back 401. */
  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.currentUser.set(null);
  }

  has(permission: Permission): boolean {
    return this.currentUser()?.permissions.includes(permission) ?? false;
  }

  hasAny(permissions: Permission[]): boolean {
    return permissions.some(permission => this.has(permission));
  }

  /** Whether the company's own admin plan even includes this module — set by a platform admin. */
  hasModule(module: BusinessModule): boolean {
    return this.currentUser()?.enabledModules.includes(module) ?? false;
  }

  /** Initials for the topbar avatar: "Ahmed Ben Ali" → "AB". */
  readonly initials = computed(() => {
    const name = this.currentUser()?.fullName ?? '';
    return name.split(' ')
      .filter(part => part.length > 0)
      .slice(0, 2)
      .map(part => part[0].toUpperCase())
      .join('');
  });

  private start(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.token);
    this.currentUser.set(response.user);
  }
}
