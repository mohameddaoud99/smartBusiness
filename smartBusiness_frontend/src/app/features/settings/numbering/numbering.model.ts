export type DocumentType =
  | 'QUOTE'
  | 'SALES_ORDER'
  | 'SALES_INVOICE'
  | 'SALES_CREDIT_NOTE'
  | 'PURCHASE_ORDER'
  | 'PURCHASE_INVOICE'
  | 'CUSTOMER';

export interface NumberingSequenceResponse {
  documentType: DocumentType;
  documentTypeLabel: string;
  prefix: string;
  padding: number;
  includeYear: boolean;
  nextValue: number;
  active: boolean;
  /** What the next document's number will look like, at the current year. */
  preview: string;
  updatedAt: string;
}

export interface NumberingSequenceRequest {
  prefix: string;
  padding: number;
  includeYear: boolean;
  nextValue: number;
  active: boolean;
}
