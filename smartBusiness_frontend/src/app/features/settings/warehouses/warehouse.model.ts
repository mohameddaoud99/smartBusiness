export interface WarehouseResponse {
  id: number;
  name: string;
  address?: string | null;
  /** The warehouse documents use when they name none — cannot be deleted or deactivated. */
  defaultWarehouse: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WarehouseRequest {
  name: string;
  address?: string | null;
  active: boolean;
}
