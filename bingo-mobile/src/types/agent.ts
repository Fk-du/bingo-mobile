import { FundStatus, Role } from './enums';

export interface AgentResponse {
  adminUserId: number;
  approved: boolean;
  businessName: string | null;
  telegramId: number;
  username: string | null;
  firstName: string | null;
  lastName: string | null;
  balance: number;
  frozenBalance: number;
  active: boolean;
}

export interface InviteCodeResponse {
  id: number;
  code: string;
  creatorId: number;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface InviteCodeStatsResponse {
  totalCodes: number;
  activeCodes: number;
  usedCodes: number;
  totalRegistrations: number;
}

export interface TenantRegistryResponse {
  id: number;
  adminUserId: number;
  databaseName: string;
  createdAt: string;
}

export interface AgentStatusRequest {
  status: string;
}

export interface AdminWarningResponse {
  id: number;
  adminUserId: number;
  reason: string;
  createdBy: number | null;
  createdAt: string;
}

export interface AgentStatsResponse {
  totalGames: number;
  endedGames: number;
  totalPlayers: number;
  totalTransactions: number;
  totalCommission: number;
  balance: number;
}

export interface OwnerFeeSettlementResponse {
  id: number;
  adminUserId: number;
  amount: number;
  screenshotUrl: string | null;
  status: FundStatus;
  approvedBy: number | null;
  approvedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
}

export interface OwnerFeeSettlementCreate {
  amount: number;
  screenshotUrl?: string;
}

export interface OwnerFeeSummaryResponse {
  accrued: number;
  settled: number;
  owed: number;
}

export interface AdminOwnerFeeSummaryResponse {
  adminUserId: number;
  businessName: string | null;
  username: string | null;
  accrued: number;
  settled: number;
  owed: number;
  lastSettledAt: string | null;
}
