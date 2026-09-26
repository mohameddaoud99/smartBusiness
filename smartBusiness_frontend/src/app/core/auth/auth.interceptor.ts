import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { NotificationService } from '../services/notification.service';

/** Sign-in and sign-up answer 401 on bad credentials — that belongs to the form, not here. */
const PUBLIC_ENDPOINTS = ['/auth/login', '/auth/register'];

/**
 * Attaches the Bearer token, and turns any other 401 into a clean sign-out.
 * A 401 on a normal call means the token expired or the account was disabled, so the
 * session is dropped and the user is sent back to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Owned by platformAuthInterceptor instead — a stale company token must never be
  // sent to the platform portal's endpoints, nor trigger the company sign-out flow.
  if (req.url.includes('/platform/')) {
    return next(req);
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const notification = inject(NotificationService);

  const token = auth.token;
  const request = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((response: HttpErrorResponse) => {
      const isPublic = PUBLIC_ENDPOINTS.some(endpoint => req.url.includes(endpoint));

      if (response.status === 401 && !isPublic) {
        // Only warn a user who believed they were signed in
        if (auth.isAuthenticated()) {
          notification.warn('Your session has ended. Please sign in again.', 'Signed out');
        }
        auth.clear();
        router.navigate(['/login']);
      }

      return throwError(() => response);
    })
  );
};
