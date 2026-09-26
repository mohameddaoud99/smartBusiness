export type ProductKind = 'GOOD' | 'SERVICE';
export type ProductPurpose = 'SALE' | 'PURCHASE' | 'BOTH';
export type ProductUnit =
  | 'PIECE'
  | 'KILOGRAM'
  | 'GRAM'
  | 'LITER'
  | 'METER'
  | 'SQUARE_METER'
  | 'BOX'
  | 'PACK'
  | 'HOUR'
  | 'DAY';

export interface CategorySummary {
  id: number;
  name: string;
}

export interface BrandSummary {
  id: number;
  name: string;
}

export interface TaxSummary {
  id: number;
  name: string;
}

export interface ProductImage {
  id: number;
  /** "data:{contentType};base64,...", ready for an img src. */
  dataUri: string;
}

export const MAX_PRODUCT_IMAGES = 4;

export interface ProductResponse {
  id: number;
  reference: string;
  name: string;
  description?: string;
  barcode?: string;
  /** The cover photo as a data URI — all the list needs. Absent when the product has none. */
  imageDataUri?: string;
  /** Every photo (up to 4), only filled when a single product is read — empty on the list. */
  images?: ProductImage[];
  kind: ProductKind;
  purpose: ProductPurpose;
  unit: ProductUnit;
  category?: CategorySummary;
  brand?: BrandSummary;
  salePrice?: number;
  purchasePrice?: number;
  allowNegativeStock: boolean;
  /** Below this available quantity the product is flagged "low stock". */
  minStock?: number | null;
  defaultTaxes: TaxSummary[];
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  reference?: string;
  name: string;
  description?: string;
  barcode?: string;
  kind: ProductKind;
  purpose: ProductPurpose;
  unit: ProductUnit;
  categoryId?: number | null;
  brandId?: number | null;
  salePrice?: number | null;
  purchasePrice?: number | null;
  allowNegativeStock: boolean;
  minStock?: number | null;
  taxIds?: number[];
  notes?: string;
}

export const KIND_OPTIONS: { label: string; value: ProductKind }[] = [
  { label: 'Product', value: 'GOOD' },
  { label: 'Service', value: 'SERVICE' }
];

export const PURPOSE_OPTIONS: { label: string; value: ProductPurpose }[] = [
  { label: 'Sale', value: 'SALE' },
  { label: 'Purchase', value: 'PURCHASE' },
  { label: 'Sale and purchase', value: 'BOTH' }
];

export const UNIT_LABELS: Record<ProductUnit, string> = {
  PIECE: 'Piece (pcs)',
  KILOGRAM: 'Kilogram (kg)',
  GRAM: 'Gram (g)',
  LITER: 'Liter (L)',
  METER: 'Meter (m)',
  SQUARE_METER: 'Square meter (m²)',
  BOX: 'Box',
  PACK: 'Pack',
  HOUR: 'Hour',
  DAY: 'Day'
};

export const UNIT_OPTIONS = (Object.keys(UNIT_LABELS) as ProductUnit[])
  .map(value => ({ label: UNIT_LABELS[value], value }));

export function kindLabel(kind: ProductKind): string {
  return kind === 'GOOD' ? 'Product' : 'Service';
}
