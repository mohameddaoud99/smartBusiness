import { ProductUnit } from '../products/product.model';

export type StockMovementType =
  | 'ENTRY'
  | 'EXIT'
  | 'ADJUSTMENT'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'RESERVE'
  | 'RELEASE';

/** The moves a user can make by hand — a transfer is two movements written together. */
export type StockAction = 'ENTRY' | 'EXIT' | 'ADJUSTMENT' | 'TRANSFER';

export interface StockLevel {
  productId: number;
  reference: string;
  name: string;
  unit: ProductUnit;
  minStock?: number | null;
  /** What is physically in the warehouse. */
  physical: number;
  /** What is promised to customers (confirmed orders). */
  reserved: number;
  /** Physical minus reserved — what is left to sell. */
  available: number;
  lowStock: boolean;
}

export interface StockMovement {
  id: number;
  type: StockMovementType;
  productId: number;
  productReference: string;
  productName: string;
  warehouseId: number;
  warehouseName: string;
  /** Signed: positive added to the stock, negative removed from it. */
  quantity: number;
  reason?: string | null;
  sourceType?: 'SALES_DOCUMENT' | null;
  sourceId?: number | null;
  occurredAt: string;
}

export interface StockMovementRequest {
  type: 'ENTRY' | 'EXIT' | 'ADJUSTMENT';
  productId: number;
  warehouseId: number;
  /** For an adjustment: the quantity COUNTED — the register writes the difference. */
  quantity: number;
  reason?: string | null;
}

export interface StockTransferRequest {
  productId: number;
  fromWarehouseId: number;
  toWarehouseId: number;
  quantity: number;
  reason?: string | null;
}

export const MOVEMENT_LABELS: Record<StockMovementType, string> = {
  ENTRY: 'Entry',
  EXIT: 'Exit',
  ADJUSTMENT: 'Adjustment',
  TRANSFER_IN: 'Transfer in',
  TRANSFER_OUT: 'Transfer out',
  RESERVE: 'Reserved',
  RELEASE: 'Released'
};

export type MovementSeverity = 'success' | 'danger' | 'warn' | 'info' | 'secondary';

const MOVEMENT_SEVERITIES: Record<StockMovementType, MovementSeverity> = {
  ENTRY: 'success',
  EXIT: 'danger',
  ADJUSTMENT: 'warn',
  TRANSFER_IN: 'info',
  TRANSFER_OUT: 'info',
  RESERVE: 'secondary',
  RELEASE: 'secondary'
};

export function movementLabel(type: StockMovementType): string {
  return MOVEMENT_LABELS[type];
}

export function movementSeverity(type: StockMovementType): MovementSeverity {
  return MOVEMENT_SEVERITIES[type];
}

export const MOVEMENT_TYPE_OPTIONS = (Object.keys(MOVEMENT_LABELS) as StockMovementType[])
  .map(value => ({ value, label: MOVEMENT_LABELS[value] }));
