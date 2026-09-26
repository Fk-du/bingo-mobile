import { GameStatus } from './enums';
import { CardResponse } from './card';

export interface CreateGameRequest {
  entryFee: number;
  maxPlayers?: number;
  winningPattern?: string;
  customPatternName?: string;
  customPatternCells?: string;
  callInterval?: number;
  commissionPercent?: number;
  autoMark?: boolean;
}

export interface GameSettingsUpdateRequest {
  maxPlayers?: number;
  callInterval?: number;
  winningPattern?: string;
  customPatternName?: string;
  customPatternCells?: string;
  commissionPercent?: number;
  autoMark?: boolean;
}

export interface GameResponse {
  id: number;
  adminUserId: number;
  status: GameStatus;
  entryFee: number;
  maxPlayers: number;
  currentCallIndex: number;
  totalNumbersCalled: number;
  prizePool: number;
  winningPattern: string | null;
  customPatternName?: string | null;
  customPatternCells?: string | null;
  autoMark: boolean;
  callInterval: number | null;
  commissionPercent?: number;
  commissionEarned?: number | null;
  startTime: string | null;
  endTime: string | null;
  createdAt: string;
  registered?: boolean;
  activeGameId?: number | null;
  registeredPlayers?: number;
}

export interface CalledNumberResponse {
  id: number;
  gameId: number;
  number: number;
  sequenceIndex: number;
  calledAt: string | null;
}

export interface FairnessProof {
  gameId: number;
  status: GameStatus;
  algorithm: string;
  fairnessHash: string | null;
  revealed: boolean;
  sequenceIntact: boolean;
  sequence: number[] | null;
  calledCount: number;
  totalNumbersCalled: number;
}

export interface PlayerCardView {
  cardId: number;
  numbers: number[][];
  winner: boolean;
  banned: boolean;
  markedNumbers?: number[] | null;
  autoMark?: boolean | null;
}

export interface GameStateResponse {
  gameId: number;
  status: GameStatus;
  winningPattern?: string | null;
  customPatternName?: string | null;
  customPatternCells?: string | null;
  autoMark: boolean;
  commissionPercent?: number | null;
  fairnessHash?: string | null;
  currentCallIndex: number;
  totalNumbersCalled: number;
  calledNumbers: number[];
  calledNumbersLabeled?: string[];
  prizePool: number;
  playerCards: PlayerCardView[] | null;
  hasPlayerCard: boolean;
  isWinner: boolean;
  startTime?: string | null;
}

export interface PendingClaimCard {
  claimId: number;
  playerId: number;
  playerName: string;
  cardId?: number | null;
  cardNumbers: number[][];
  calledNumbers: number[];
  claimedAt?: string | null;
}

export interface BingoClaimResponse {
  id: number;
  gameId: number;
  playerId: number;
  cardId: number;
  cardSnapshot: string | null;
  calledNumbersSnapshot: string | null;
  result: string;
  rewardAmount: number | null;
  validatedBy: number | null;
  rejectionReason: string | null;
  claimedAt: string | null;
  validatedAt: string | null;
}

export interface BingoClaimResultResponse {
  valid: boolean;
  claimId?: number;
  pendingReview?: boolean;
  gameEnded?: boolean;
  approvedCount?: number;
  rewardAmount: number;
  commission?: number;
  banned: boolean;
  restarted?: boolean;
}

export interface RegisterResponse {
  gameId: number;
  cardId: number;
  cardIds?: number[];
}

export interface AdminGameStateResponse {
  gameId: number;
  status: GameStatus;
  entryFee: number;
  maxPlayers: number;
  currentCallIndex: number;
  totalNumbersCalled: number;
  prizePool: number;
  winningPattern: string | null;
  customPatternName?: string | null;
  customPatternCells?: string | null;
  autoMark: boolean;
  callInterval: number | null;
  commissionPercent?: number;
  startTime: string | null;
  endTime: string | null;
  createdAt: string;
  calledNumbers: number[];
  calledNumbersLabeled?: string[];
  playerCount: number;
}

export interface AutomationConfig {
  adminUserId: number;
  enabled: boolean;
  entryFee: number;
  maxPlayers: number;
  callInterval: number;
  commissionPercent: number;
  winningPattern: string | null;
  customPatternName?: string | null;
  customPatternCells?: string | null;
  autoMark: boolean;
  registrationWindowSeconds: number;
  cooldownSeconds: number;
  startWhenFull: boolean;
  nextGameAt?: string | null;
  updatedAt?: string | null;
}

export interface AutomationConfigRequest {
  entryFee: number;
  maxPlayers: number;
  callInterval: number;
  commissionPercent: number;
  winningPattern?: string;
  customPatternName?: string;
  customPatternCells?: string;
  autoMark?: boolean;
  registrationWindowSeconds: number;
  cooldownSeconds: number;
  startWhenFull?: boolean;
  enabled?: boolean;
}

export interface GameCardResponse {
  id: number;
  gameId: number;
  playerId: number;
  card: CardResponse;
  winner: boolean;
  createdAt: string;
}

export interface PlayerCardHistoryCard {
  cardId: number;
  winner: boolean;
  banned: boolean;
  registeredAt: string;
  claimResult: 'VALID' | 'REJECTED' | null;
  claimedAt: string | null;
  validatedAt: string | null;
  rejectionReason: string | null;
}

export interface PlayerCardHistory {
  game: GameResponse;
  cards: PlayerCardHistoryCard[];
  bet: number;
  win: number;
  refund: number;
  net: number;
}
