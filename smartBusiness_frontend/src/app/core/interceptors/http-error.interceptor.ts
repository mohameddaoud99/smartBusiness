import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../services/notification.service';

/** Shape returned by the backend's GlobalExceptionHandler. */
interface ApiError {
  status: number;
  message: string;
  errors?: string[];
}

export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const notification = inject(NotificationService);

  return next(req).pipe(
    catchError((response: HttpErrorResponse) => {
      const body = response.error as ApiError | null;

      if (response.status === 401) {
        // Owned by authInterceptor (session ended) or by the sign-in form itself
        return throwError(() => response);
      }

      if (response.status === 0) {
        notification.error('Cannot reach the server. Check that the backend is running.');
      } else if (response.status === 400 && body?.errors?.length) {
        // Validation: show the field messages the backend produced
        notification.error(body.errors.join(' · '), 'Please check the form');
      } else if (body?.message) {
        // 404 / 409 / 422 — the backend message is already user-facing
        notification.error(body.message);
      } else {
        notification.error('An unexpected error occurred. Please try again.');
      }

      return throwError(() => response);
    })
  );
};
