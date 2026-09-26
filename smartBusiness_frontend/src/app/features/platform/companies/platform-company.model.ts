import { BusinessModule } from '../../../core/auth/session.model';

export type { BusinessModule };

export type CompanyStatus = 'ACTIVE' | 'SUSPENDED';

export interface PlatformCompanyResponse {
  id: number;
  name: string;
  email?: string;
  status: CompanyStatus;
  enabledModules: BusinessModule[];
  createdAt: string;
}

export interface UpdateCompanyModulesRequest {
  modules: BusinessModule[];
}

export const MODULE_OPTIONS: { value: BusinessModule; label: string }[] = [
  { value: 'CUSTOMERS', label: 'Customers' },
  { value: 'SALES', label: 'Sales' },
  { value: 'PURCHASES', label: 'Purchases' },
  { value: 'INVENTORY', label: 'Inventory' }
];
