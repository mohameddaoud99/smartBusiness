import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { MessageService, ConfirmationService } from 'primeng/api';

import { routes } from './app.routes';
import { SmartBusinessPreset } from './theme';
import { httpErrorInterceptor } from './core/interceptors/http-error.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { platformAuthInterceptor } from './core/platform-auth/platform-auth.interceptor';
import { PlatformAuthService } from './core/platform-auth/platform-auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    // authInterceptor and platformAuthInterceptor each own a disjoint set of URLs
    // (company vs. /api/platform/**), so their order relative to each other never matters.
    provideHttpClient(withInterceptors([authInterceptor, platformAuthInterceptor, httpErrorInterceptor])),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: {
        preset: SmartBusinessPreset,
        options: {
          darkModeSelector: '.dark-mode'
        }
      },
      ripple: false
    }),
    MessageService,
    ConfirmationService,
    // A stored token is validated against the backend before the first route resolves,
    // so guards never run against a stale session. Both sessions are independent, so
    // one failing to restore never blocks the other.
    provideAppInitializer(() => inject(AuthService).restore()),
    provideAppInitializer(() => inject(PlatformAuthService).restore())
  ]
};
