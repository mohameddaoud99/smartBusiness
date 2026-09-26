export interface CompanyBankAccountResponse {
  id: number;
  label: string;
  bankName?: string;
  rib: string;
  currency: string;
  showOnDocuments: boolean;
  createdAt: string;
  updatedAt: string;
}

/** A PUT replaces the whole account — every field is sent. */
export interface CompanyBankAccountRequest {
  label: string;
  bankName?: string;
  rib: string;
  currency: string;
  showOnDocuments: boolean;
}
