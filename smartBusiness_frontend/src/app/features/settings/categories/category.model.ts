export interface CategoryResponse {
  id: number;
  name: string;
  parentId?: number;
  parentName?: string;
  /** How many products point here — guides the delete confirmation. */
  productCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryRequest {
  name: string;
  /** Null makes this a top-level family. */
  parentId?: number | null;
}
