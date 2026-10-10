import { Role } from './enums';

export interface DepositAccount {
  bank: string | null;
  accountNumber: string;
  ownerName: string | null;
}

export interface LoginRequest {
  initData: string;
  startParam?: string;
}

export interface UserProfileResponse {
  id: number;
  telegramId: number;
  username: string | null;
  firstName: string | null;
  lastName: string | null;
  phoneNumber: string | null;
  role: Role;
  verified: boolean;
  adminUserId: number | null;
  businessName: string | null;
  depositAccounts: DepositAccount[] | null;
  adminApproved: boolean;
  parentId: number | null;
  balance: number;
  frozenBalance: number;
  active: boolean;
  preferredLanguage: string | null;
}
