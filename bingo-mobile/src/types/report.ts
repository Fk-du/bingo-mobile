import { GameResponse } from './game';

export interface RevenueReportResponse {
  totalGames: number;
  totalTransactions: number;
  totalPlayers: number;
  balance: number;
  platformFee?: number;
  totalCommission?: number;
  todayCommission?: number;
}

export interface DashboardSummaryResponse {
  totalPlayers: number;
  recentPlayers: PlayerSummary[];
  totalGames: number;
  pendingClaimsCount: number;
  recentGames: GameResponse[];
  pendingCoinRequests: number;
  pendingWithdrawals: number;
  balance: number;
}

export interface PlayerSummary {
  id: number;
  userId: number;
  firstName?: string;
  lastName?: string;
  username?: string;
}
