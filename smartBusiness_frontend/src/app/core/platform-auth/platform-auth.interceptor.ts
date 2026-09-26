import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { PlatformAuthService } from './platform-auth.service';

/**
 * Attaches the platform Bearer token, only for `/api/platform/**` calls — the company
 * `authInterceptor` explicitly steps aside for those same URLs, so a request never
 * carries both tokens or the wrong one.
 */
export const platformAuthInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/platform/')) {
    return next(req);
  }

  const platformAuth = inject(PlatformAuthService);
  const router = inject(Router);

  const token = platformAuth.token;
  const request = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((response: HttpErrorResponse) => {
      const isLogin = req.url.includes('/platform/auth/login');

      if (response.status === 401 && !isLogin) {
        platformAuth.clear();
        router.navigate(['/platform/login']);
      }

      return throwError(() => response);
    })
  );
};
