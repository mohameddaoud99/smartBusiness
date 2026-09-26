import { Component, computed, inject, signal, HostListener, OnInit } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { PopoverModule } from 'primeng/popover';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { BadgeModule } from 'primeng/badge';
import { TooltipModule } from 'primeng/tooltip';
import { MenuItem } from 'primeng/api';
import { filter } from 'rxjs';

import { LayoutService } from '../layout.service';
import { AuthService } from '../../core/auth/auth.service';

interface Crumb {
  label: string;
  route?: string;
}

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    ButtonModule, MenuModule, PopoverModule, DialogModule,
    InputTextModule, BadgeModule, TooltipModule
  ],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss'
})
export class HeaderComponent implements OnInit {

  readonly layout = inject(LayoutService);
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly crumbs = signal<Crumb[]>([]);
  readonly searchOpen = signal(false);

  // Placeholder until the notification backend exists
  readonly notificationCount = 0;

  /** Roles are shown under the name — "Sales Manager, Stock Manager". */
  readonly roleLabels = computed(() =>
    this.auth.session()?.roles.map(role => role.label).join(', ') || 'No role assigned'
  );

  readonly userMenu: MenuItem[] = [
    {
      label: 'My profile',
      icon: 'pi pi-user',
      command: () => this.router.navigate(['/settings/profile'])
    },
    {
      label: 'Settings',
      icon: 'pi pi-cog',
      command: () => this.router.navigate(['/settings'])
    },
    { separator: true },
    { label: 'Sign out', icon: 'pi pi-sign-out', command: () => this.auth.logout() }
  ];

  ngOnInit() {
    this.buildCrumbs();
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => this.buildCrumbs());
  }

  /** Ctrl+K / Cmd+K opens global search — a convention business users expect. */
  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent) {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.searchOpen.set(true);
    }
  }

  /**
   * Builds the trail from `breadcrumb` values declared on the routes.
   * Example: { path: 'users', data: { breadcrumb: 'Users' } }
   */
  private buildCrumbs() {
    const crumbs: Crumb[] = [];
    let route = this.route.root;
    let url = '';

    while (route.firstChild) {
      route = route.firstChild;
      const segment = route.snapshot.url.map(s => s.path).join('/');
      if (segment) {
        url += `/${segment}`;
      }
      const label = route.snapshot.data['breadcrumb'];
      if (label) {
        crumbs.push({ label, route: url });
      }
    }

    this.crumbs.set(crumbs);
  }
}
