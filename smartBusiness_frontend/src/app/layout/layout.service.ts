import { Injectable, signal, computed } from '@angular/core';

const MOBILE_BREAKPOINT = 992;
const COLLAPSED_KEY = 'sc.sidebar.collapsed';

/**
 * Holds the sidebar state shared by the layout components.
 *
 * Three display modes, derived from two signals:
 *   expanded   → desktop, labels visible
 *   collapsed  → desktop, icon rail + tooltips
 *   overlay    → below 992px, sidebar slides over the content
 */
@Injectable({ providedIn: 'root' })
export class LayoutService {

  readonly isMobile = signal(window.innerWidth < MOBILE_BREAKPOINT);
  readonly collapsed = signal(localStorage.getItem(COLLAPSED_KEY) === 'true');
  readonly mobileOpen = signal(false);

  /** On mobile the sidebar is always full width when open — never a rail. */
  readonly showLabels = computed(() => this.isMobile() || !this.collapsed());

  toggle() {
    if (this.isMobile()) {
      this.mobileOpen.update(open => !open);
    } else {
      this.collapsed.update(collapsed => {
        localStorage.setItem(COLLAPSED_KEY, String(!collapsed));
        return !collapsed;
      });
    }
  }

  closeMobile() {
    this.mobileOpen.set(false);
  }

  onResize(width: number) {
    const mobile = width < MOBILE_BREAKPOINT;
    this.isMobile.set(mobile);
    if (!mobile) {
      this.mobileOpen.set(false);
    }
  }
}
