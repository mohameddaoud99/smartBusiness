import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';
import { NotificationService } from '../services/notification.service';
import { Permission } from './session.model';

/** Blocks anonymous access and remembers where the user was heading. */
export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { redirectTo: state.url } });
};

/**
 * Reads `data.permission` from the route.
 * This is user experience, not security — the backend refuses the call regardless.
 */
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notification = inject(NotificationService);

  const permission = route.data['permission'] as Permission | undefined;

  if (!permission || auth.has(permission)) {
    return true;
  }

  notification.warn('You do not have access to that page.');
  return router.createUrlTree(['/dashboard']);
};
