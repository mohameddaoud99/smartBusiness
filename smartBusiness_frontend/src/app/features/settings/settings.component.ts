import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { AuthService } from '../../core/auth/auth.service';

interface SettingsTile {
  label: string;
  description: string;
  icon: string;
  /** Present once the screen exists and the user may open it. */
  route?: string;
  /** Shown but not clickable — on the roadmap, not built yet. */
  soon?: boolean;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent {

  private readonly auth = inject(AuthService);

  readonly tiles: SettingsTile[] = [
    {
      label: 'My profile',
      description: 'Your name, contact details and password',
      icon: 'pi pi-user',
      route: '/settings/profile'
    },
    {
      label: 'Company',
      description: 'Business identity, tax details and branding',
      icon: 'pi pi-building',
      route: this.auth.has('COMPANY_VIEW') ? '/settings/company' : undefined,
      soon: false
    },
    {
      label: 'Document numbering',
      description: 'Prefixes and sequences for quotes, invoices and other documents',
      icon: 'pi pi-hashtag',
      route: this.auth.has('COMPANY_VIEW') ? '/settings/numbering' : undefined
    },
    {
      label: 'Taxes',
      description: 'VAT rates, stamp duty and other document taxes',
      icon: 'pi pi-percentage',
      route: this.auth.has('COMPANY_VIEW') ? '/settings/taxes' : undefined
    },
    {
      label: 'Bank accounts',
      description: 'Accounts shown on your invoices and used to record payments',
      icon: 'pi pi-wallet',
      route: this.auth.has('COMPANY_VIEW') ? '/settings/bank-accounts' : undefined
    },
    {
      label: 'Categories',
      description: 'Product families, optionally nested under one another',
      icon: 'pi pi-sitemap',
      route: this.auth.has('PRODUCT_VIEW') ? '/settings/categories' : undefined
    },
    {
      label: 'Warehouses',
      description: 'Where your stock is kept',
      icon: 'pi pi-warehouse',
      route: this.auth.has('STOCK_VIEW') ? '/settings/warehouses' : undefined
    },
    {
      label: 'Brands',
      description: 'Manufacturer labels shown on products',
      icon: 'pi pi-tag',
      route: this.auth.has('PRODUCT_VIEW') ? '/settings/brands' : undefined
    }
  ].filter(tile => tile.route || tile.soon);
}
