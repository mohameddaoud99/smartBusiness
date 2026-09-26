export type BranchStatus = 'ACTIVE' | 'INACTIVE';

/** The little of a branch other screens need — a user row, a user's own detail. */
export interface BranchSummary {
  id: number;
  code: string;
  name: string;
}

export interface BranchResponse {
  id: number;
  code: string;
  name: string;
  address?: string;
  phone?: string;
  status: BranchStatus;
  createdAt: string;
  updatedAt: string;
}

/** Status is absent on purpose — a branch is enabled or disabled through its own actions. */
export interface BranchRequest {
  code: string;
  name: string;
  address?: string;
  phone?: string;
}
