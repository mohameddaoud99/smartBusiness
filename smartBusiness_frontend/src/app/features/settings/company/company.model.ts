export type CompanyStatus = 'ACTIVE' | 'SUSPENDED';

export interface CompanyResponse {
  id: number;
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  postalCode?: string;
  city?: string;
  taxId?: string;
  currency: string;
  status: CompanyStatus;
  /** "data:{contentType};base64,...", ready for an <img [src]>. Undefined when none is set. */
  logoDataUri?: string;
  stampDataUri?: string;
  createdAt: string;
  updatedAt: string;
}

/** Status is never sent — suspending a company is a platform decision, not a tenant one. */
export interface CompanyRequest {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  postalCode?: string;
  city?: string;
  taxId?: string;
  currency: string;
}

/** A practical shortlist for the picker — the backend accepts any 3-letter ISO code. */
export const CURRENCY_OPTIONS: { label: string; value: string }[] = [
  { label: 'Tunisian Dinar (TND)', value: 'TND' },
  { label: 'Euro (EUR)', value: 'EUR' },
  { label: 'US Dollar (USD)', value: 'USD' },
  { label: 'Moroccan Dirham (MAD)', value: 'MAD' },
  { label: 'Algerian Dinar (DZD)', value: 'DZD' },
  { label: 'Swiss Franc (CHF)', value: 'CHF' },
  { label: 'British Pound (GBP)', value: 'GBP' },
  { label: 'Saudi Riyal (SAR)', value: 'SAR' },
  { label: 'UAE Dirham (AED)', value: 'AED' },
  { label: 'Canadian Dollar (CAD)', value: 'CAD' }
];
