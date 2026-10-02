import { GameStatus } from './enums';
import { CardResponse } from './card';

export interface CreateGameRequest {
  entryFee: number;
  winningPattern?: string;
  callInterval?: number;
  autoMark?: boolean;
}

export interface GameSettingsUpdateRequest {
  callInterval?: number;
  winningPattern?: string;
  /** Total the winners share. The admin sets it once players have registered. */
  prizeAmount?: number;
  autoMark?: boolean;
}

/** A game as its ADMIN sees it: the full financial picture. */
export interface AdminGameResponse {
  id: number;
  adminUserId: number;
  status: GameStatus;
  entryFee: number;
  currentCallIndex: number;
  totalNumbersCalled: number;
  prizePool: number;
  /** Total the winners share. Null until the admin sets it. */
  prizeAmount?: number | null;
  winningPattern: string | null;
  autoMark: boolean;
  callInterval: number | null;
  commissionEarned?: number | null;
  /** Lowest / highest prize the admin may set for the current pot. */
  minPrize?: number | null;
  maxPrize?: number | null;
  startTime: string | null;
  endTime: string | null;
  createdAt: string;
  registered?: boolean;
  activeGameId?: number | null;
  registeredPlayers?: number;
}

/**
 * A game as a PLAYER sees it. The pot, the admin's cut and the number of other
 * players are deliberately absent — the backend does not send them.
 */
export interface PlayerGameResponse {
  id: number;
  adminUserId: number;
  status: GameStatus;
  entryFee: number;
  currentCallIndex: number;
  totalNumbersCalled: number;
  /** The advertised payout. Public, because it is what the player plays for. */
  prizeAmount?: number | null;
  winningPattern: string | null;
  autoMark: boolean;
  callInterval: number | null;
  startTime: string | null;
  endTime: string | null;
  createdAt: string;
  registered?: boolean;
  activeGameId?: number | null;
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

/**
 * A card the player is looking at but has not paid for. It is held so nobody
 * else is dealt the same numbers, but it is not a registration: no entry fee
 * has been taken and it cannot claim Bingo until it is registered.
 */
export interface PreviewCardView {
  cardId: number;
  numbers: number[][];
}

/** Result of removing a card from the player's board. */
export interface CardRemovalResponse {
  /** False when the card was only a preview, so nothing was refunded. */
  wasRegistered: boolean;
  refund: number;
}

export interface GameStateResponse {
  gameId: number;
  status: GameStatus;
  winningPattern?: string | null;
  autoMark: boolean;
  fairnessHash?: string | null;
  currentCallIndex: number;
  totalNumbersCalled: number;
  calledNumbers: number[];
  calledNumbersLabeled?: string[];
  /** The advertised payout. The pot it came from is not sent to players. */
  prizeAmount?: number | null;
  playerCards: PlayerCardView[] | null;
  /** Unpaid cards held for review. Empty once the game leaves registration. */
  previewCards: PreviewCardView[] | null;
  hasPlayerCard: boolean;
  isWinner: boolean;
  /** The winner's own share of the prize; null for anyone who did not win. */
  rewardAmount?: number | null;
  /** How many cards won this game, so a winner can see the prize was shared. */
  winnerCount?: number | null;
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
  currentCallIndex: number;
  totalNumbersCalled: number;
  prizePool: number;
  winningPattern: string | null;
  autoMark: boolean;
  callInterval: number | null;
  prizeAmount?: number | null;
  minPrize?: number | null;
  maxPrize?: number | null;
  startTime: string | null;
  endTime: string | null;
  createdAt: string;
  calledNumbers: number[];
  calledNumbersLabeled?: string[];
  playerCount: number;
}

/** What the admin needs to choose a prize: the pot, the allowed band, a suggestion. */
export interface PrizeSuggestion {
  collected: number;
  minPrize: number;
  maxPrize: number;
  suggestedPrize: number;
  suggestedCommission: number;
  currentPrize?: number | null;
}

export interface AutomationConfig {
  adminUserId: number;
  enabled: boolean;
  entryFee: number;
  callInterval: number;
  /** The rake this admin prefers, used to pre-fill the suggested prize. */
  rakePercent: number;
  winningPattern: string | null;
  autoMark: boolean;
  registrationWindowSeconds: number;
  cooldownSeconds: number;
  nextGameAt?: string | null;
  updatedAt?: string | null;
}

export interface AutomationConfigRequest {
  entryFee: number;
  callInterval: number;
  rakePercent: number;
  winningPattern?: string;
  autoMark?: boolean;
  registrationWindowSeconds: number;
  cooldownSeconds: number;
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
  game: PlayerGameResponse;
  cards: PlayerCardHistoryCard[];
  bet: number;
  win: number;
  refund: number;
  net: number;
}
