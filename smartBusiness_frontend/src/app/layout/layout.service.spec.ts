import { TestBed } from '@angular/core/testing';
import { LayoutService } from './layout.service';

describe('LayoutService', () => {

  let service: LayoutService;

  beforeEach(() => {
    localStorage.removeItem('sc.sidebar.collapsed');
    TestBed.configureTestingModule({});
    service = TestBed.inject(LayoutService);
  });

  it('collapses and expands on desktop', () => {
    service.onResize(1400);
    expect(service.collapsed()).toBeFalse();
    expect(service.showLabels()).toBeTrue();

    service.toggle();
    expect(service.collapsed()).toBeTrue();
    expect(service.showLabels()).toBeFalse();

    service.toggle();
    expect(service.collapsed()).toBeFalse();
  });

  it('remembers the collapsed state', () => {
    service.onResize(1400);
    service.toggle();
    expect(localStorage.getItem('sc.sidebar.collapsed')).toBe('true');
  });

  it('opens an overlay instead of a rail on small screens', () => {
    service.onResize(700);
    expect(service.isMobile()).toBeTrue();

    service.toggle();
    expect(service.mobileOpen()).toBeTrue();
    // Labels stay visible on mobile — the sidebar is full width, never a rail
    expect(service.showLabels()).toBeTrue();

    service.closeMobile();
    expect(service.mobileOpen()).toBeFalse();
  });

  it('closes the overlay when the window grows back to desktop', () => {
    service.onResize(700);
    service.toggle();
    expect(service.mobileOpen()).toBeTrue();

    service.onResize(1400);
    expect(service.mobileOpen()).toBeFalse();
  });
});
