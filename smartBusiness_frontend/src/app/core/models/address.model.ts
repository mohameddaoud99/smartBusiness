/** Billing / shipping address, shared by any feature that embeds one (customers, suppliers…). */
export interface Address {
  street?: string;
  city?: string;
  region?: string;
  postalCode?: string;
  country?: string;
}
