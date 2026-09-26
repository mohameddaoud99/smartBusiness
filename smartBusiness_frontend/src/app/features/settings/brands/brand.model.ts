export interface BrandResponse {
  id: number;
  name: string;
  /** How many products point here — guides the delete confirmation. */
  productCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface BrandRequest {
  name: string;
}
