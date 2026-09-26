import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { PlatformAuthService } from './platform-auth.service';

export const platformAuthGuard: CanActivateFn = () => {
  const auth = inject(PlatformAuthService);
  const router = inject(Router);

  return auth.isAuthenticated() ? true : router.createUrlTree(['/platform/login']);
};
