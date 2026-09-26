import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { Permission } from '../../core/auth/session.model';

/**
 * Hides an element the current user has no permission for.
 *
 *   <p-button *appHasPermission="'USER_CREATE'" label="New user" />
 *   <div *appHasPermission="['ROLE_UPDATE', 'ROLE_DELETE']">…</div>
 *
 * With an array, holding any one of the permissions is enough.
 *
 * This is decluttering, not security: the backend re-checks every call. Never rely on
 * it to protect anything.
 */
@Directive({
  selector: '[appHasPermission]',
  standalone: true
})
export class HasPermissionDirective {

  private readonly auth = inject(AuthService);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly templateRef = inject(TemplateRef<unknown>);

  readonly permission = input.required<Permission | Permission[]>({ alias: 'appHasPermission' });

  constructor() {
    // Re-evaluated on its own whenever the session signal changes
    effect(() => {
      const required = this.permission();
      const allowed = Array.isArray(required)
        ? this.auth.hasAny(required)
        : this.auth.has(required);

      this.viewContainer.clear();
      if (allowed) {
        this.viewContainer.createEmbeddedView(this.templateRef);
      }
    });
  }
}
