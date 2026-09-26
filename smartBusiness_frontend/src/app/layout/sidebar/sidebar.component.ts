import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { TooltipModule } from 'primeng/tooltip';
import { filter } from 'rxjs';

import { LayoutService } from '../layout.service';
import { NAVIGATION, NavItem, NavSection } from '../navigation';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TooltipModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent implements OnInit {

  readonly layout = inject(LayoutService);
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /**
   * The navigation this user is allowed to see. An item whose permission they lack, or
   * whose module their company was not granted, is dropped — and a group left with no
   * visible child goes with it, no dead ends.
   */
  readonly sections = computed<NavSection[]>(() =>
    NAVIGATION
      .map(section => ({ ...section, items: this.visibleItems(section.items) }))
      .filter(section => section.items.length > 0)
  );

  /** Labels of the currently expanded groups. */
  readonly openGroups = signal<string[]>([]);

  ngOnInit() {
    this.openActiveGroup(this.router.url);

    // Keep the group containing the active route open after every navigation
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => this.openActiveGroup(event.urlAfterRedirects));
  }

  toggleGroup(label: string) {
    // A collapsed rail has no room for children — expand the sidebar first
    if (!this.layout.showLabels()) {
      this.layout.toggle();
      this.openGroups.set([label]);
      return;
    }

    this.openGroups.update(open =>
      open.includes(label) ? open.filter(l => l !== label) : [...open, label]
    );
  }

  isGroupOpen(label: string): boolean {
    return this.openGroups().includes(label);
  }

  /** A collapsed group still shows it holds the active page. */
  isGroupActive(item: NavItem, url: string): boolean {
    return !!item.children?.some(child => child.route && url.startsWith(child.route));
  }

  get currentUrl(): string {
    return this.router.url;
  }

  onNavigate() {
    if (this.layout.isMobile()) {
      this.layout.closeMobile();
    }
  }

  private visibleItems(items: NavItem[]): NavItem[] {
    return items
      .filter(item => !item.permission || this.auth.has(item.permission))
      .filter(item => !item.module || this.auth.hasModule(item.module))
      .map(item => item.children
        ? { ...item, children: this.visibleItems(item.children) }
        : item)
      .filter(item => !item.children || item.children.length > 0);
  }

  private openActiveGroup(url: string) {
    const active = this.sections()
      .flatMap(section => section.items)
      .filter(item => this.isGroupActive(item, url))
      .map(item => item.label);

    if (active.length) {
      this.openGroups.update(open => [...new Set([...open, ...active])]);
    }
  }
}
