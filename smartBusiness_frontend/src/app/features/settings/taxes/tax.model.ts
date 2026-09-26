export type TaxKind = 'VAT_RATE' | 'PERCENTAGE_SURCHARGE' | 'FIXED_PER_DOCUMENT';

export interface TaxResponse {
  id: number;
  name: string;
  kind: TaxKind;
  rate?: number;
  amount?: number;
  includedInVatBase: boolean;
  appliesToLine: boolean;
  activeByDefault: boolean;
  active: boolean;
  system: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TaxRequest {
  name: string;
  kind: TaxKind;
  rate?: number | null;
  amount?: number | null;
  includedInVatBase: boolean;
  activeByDefault: boolean;
  active: boolean;
}

export const TAX_KIND_LABELS: Record<TaxKind, string> = {
  VAT_RATE: 'VAT rate',
  PERCENTAGE_SURCHARGE: 'Percentage surcharge',
  FIXED_PER_DOCUMENT: 'Fixed amount per document'
};

export const TAX_KIND_OPTIONS = (Object.keys(TAX_KIND_LABELS) as TaxKind[])
  .map(value => ({ label: TAX_KIND_LABELS[value], value }));
