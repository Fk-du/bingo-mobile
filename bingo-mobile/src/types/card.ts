import { AssignmentStatus, FundStatus } from './enums';

export interface CardResponse {
  id: number;
  numbers: string;
  numbersHash: string;
  used: boolean;
  usageCount: number;
  gamesWon?: number;
  winRate: number;
  createdAt: string;
  /** Parsed 5x5 grid (backend renders the card JSON), 0 = free centre. */
  grid?: number[][];
}

export interface CardPoolResponse {
  cards: CardResponse[];
  total: number;
  page: number;
  size: number;
}

export interface CardRequestResponse {
  id: number;
  adminUserId: number;
  quantity: number;
  status: FundStatus;
  approvedBy: number | null;
  approvedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
}

export interface PlayerCardResponse {
  id: number;
  playerId: number;
  card: CardResponse;
  status: AssignmentStatus;
  gamesPlayed: number;
  gamesWon: number;
  assignedAt: string | null;
  unassignedAt: string | null;
}
