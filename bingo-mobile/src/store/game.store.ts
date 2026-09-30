import { create } from 'zustand';
import { GameStatus, CalledNumberResponse, BingoClaimResponse, PlayerCardView } from '@/types';

interface GameState {
  activeGameId: number | null;
  gameStatus: GameStatus | null;
  startTime: string | null;
  /** Why the game is counting down: start | resume | restart | claim_resolved. */
  startReason: string | null;
  calledNumbers: CalledNumberResponse[];
  totalNumbersCalled: number;
  /** The advertised payout. Players never see the pot it came from. */
  prizeAmount: number | null;
  playerCards: PlayerCardView[] | null;
  isConnecting: boolean;
  claimPending: BingoClaimResponse | null;
  restartNotice: string | null;
  setActiveGame: (gameId: number) => void;
  setGameStatus: (status: GameStatus) => void;
  setStartTime: (time: string | null) => void;
  setStartReason: (reason: string | null) => void;
  addCalledNumber: (number: CalledNumberResponse) => void;
  setCalledNumbers: (numbers: CalledNumberResponse[]) => void;
  clearCalledNumbers: () => void;
  setTotalNumbersCalled: (count: number) => void;
  setPrizeAmount: (prize: number | null) => void;
  setPlayerCards: (cards: PlayerCardView[] | null) => void;
  setConnecting: (connecting: boolean) => void;
  setClaimPending: (claim: BingoClaimResponse | null) => void;
  setRestartNotice: (message: string | null) => void;
  reset: () => void;
}

export const useGameStore = create<GameState>((set) => ({
  activeGameId: null,
  gameStatus: null,
  startTime: null,
  startReason: null,
  calledNumbers: [],
  totalNumbersCalled: 0,
  prizeAmount: null,
  playerCards: null,
  isConnecting: true,
  claimPending: null,
  restartNotice: null,
  setActiveGame: (gameId) => set({ activeGameId: gameId }),
  setGameStatus: (status) => set({ gameStatus: status }),
  setStartTime: (time) => set({ startTime: time }),
  setStartReason: (reason) => set({ startReason: reason }),
  addCalledNumber: (number) =>
    set((state) => {
      // A reconnect can replay events, so never let the board count drift.
      if (state.calledNumbers.some((c) => c.number === number.number)) return state;
      return { calledNumbers: [...state.calledNumbers, number] };
    }),
  setCalledNumbers: (numbers) =>
    set((state) => {
      // A poll that started before the newest socket event can resolve after it
      // and carry fewer numbers. Merging keeps the board monotonic, so an
      // out-of-order response can never wipe numbers already on screen.
      // Use clearCalledNumbers for a genuine new deal.
      if (numbers.length === 0) return state;
      const byNumber = new Map<number, CalledNumberResponse>();
      for (const existing of state.calledNumbers) byNumber.set(existing.number, existing);
      for (const incoming of numbers) {
        const previous = byNumber.get(incoming.number);
        if (!previous || incoming.sequenceIndex > previous.sequenceIndex) {
          byNumber.set(incoming.number, incoming);
        }
      }
      const merged = [...byNumber.values()].sort((a, b) => a.sequenceIndex - b.sequenceIndex);
      // The union can only grow, so an unchanged length means nothing is new
      // and the existing reference can be kept to avoid a re-render.
      if (merged.length === state.calledNumbers.length) return state;
      return { calledNumbers: merged };
    }),
  clearCalledNumbers: () => set({ calledNumbers: [] }),
  setTotalNumbersCalled: (count) => set({ totalNumbersCalled: count }),
  setPrizeAmount: (prize) => set({ prizeAmount: prize }),
  setPlayerCards: (cards) => set({ playerCards: cards }),
  setConnecting: (connecting) => set({ isConnecting: connecting }),
  setClaimPending: (claim) => set({ claimPending: claim }),
  setRestartNotice: (message) => set({ restartNotice: message }),
  reset: () => set({
    activeGameId: null, gameStatus: null, startTime: null, startReason: null, calledNumbers: [],
    totalNumbersCalled: 0, prizeAmount: null, playerCards: null, claimPending: null, restartNotice: null,
  }),
}));