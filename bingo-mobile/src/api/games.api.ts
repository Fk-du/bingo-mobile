import apiClient from './client';
import {
  AdminGameStateResponse,
  ApiResponse,
  AdminGameResponse,
  PlayerGameResponse,
  PrizeSuggestion,
  GameStateResponse,
  BingoClaimResponse,
  BingoClaimResultResponse,
  PendingClaimCard,
  PreviewCardView,
  CardRemovalResponse,
  RegisterResponse,
  CreateGameRequest,
  GameSettingsUpdateRequest,
  AutomationConfig,
  AutomationConfigRequest,
  CalledNumberResponse,
  FairnessProof,
  PlayerCardHistory,
} from '@/types';

export const gamesApi = {
  create: async (data: CreateGameRequest) => {
    const res = await apiClient.post<ApiResponse<AdminGameResponse>>('/games', data);
    return res.data;
  },
  updateSettings: async (id: number, data: GameSettingsUpdateRequest) => {
    const res = await apiClient.patch<ApiResponse<AdminGameResponse>>(`/games/${id}/settings`, data);
    return res.data;
  },
  callNext: async (id: number) => {
    const res = await apiClient.post<ApiResponse<CalledNumberResponse | string>>(`/games/${id}/call-next`);
    return res.data;
  },
  callSpecific: async (id: number, number: number) => {
    const res = await apiClient.post<ApiResponse<string>>(`/games/${id}/call/${number}`);
    return res.data;
  },
  start: async (id: number) => {
    const res = await apiClient.post<ApiResponse<AdminGameResponse>>(`/games/${id}/start`);
    return res.data;
  },
  cancel: async (id: number) => {
    const res = await apiClient.post<ApiResponse<string>>(`/games/${id}/cancel`);
    return res.data;
  },
  pause: async (id: number) => {
    const res = await apiClient.post<ApiResponse<string>>(`/games/${id}/pause`);
    return res.data;
  },
  resume: async (id: number) => {
    const res = await apiClient.post<ApiResponse<string>>(`/games/${id}/resume`);
    return res.data;
  },
  end: async (id: number) => {
    const res = await apiClient.post<ApiResponse<string>>(`/games/${id}/end`);
    return res.data;
  },
  getActive: async () => {
    const res = await apiClient.get<ApiResponse<AdminGameResponse[]>>('/games/active');
    return res.data;
  },
  register: async (id: number, count: number) => {
    const res = await apiClient.post<ApiResponse<RegisterResponse>>(`/games/${id}/register`, { count });
    return res.data;
  },
  /**
   * Hold `count` cards for the player to look at. Nothing is charged: each card is
   * registered on its own afterwards, or dropped with removeCard.
   */
  previewCards: async (id: number, count: number) => {
    const res = await apiClient.post<ApiResponse<PreviewCardView[]>>(`/games/${id}/cards/preview`, { count });
    return res.data;
  },
  /** Pay the entry fee for one previewed card and deal it to the player. */
  registerCard: async (id: number, cardId: number) => {
    const res = await apiClient.post<ApiResponse<RegisterResponse>>(
      `/games/${id}/cards/${cardId}/register`
    );
    return res.data;
  },
  /**
   * Take a card off the player's board. An unpaid preview is simply released; a
   * registered card is unregistered and its entry fee is refunded.
   */
  removeCard: async (id: number, cardId: number) => {
    const res = await apiClient.delete<ApiResponse<CardRemovalResponse>>(`/games/${id}/cards/${cardId}`);
    return res.data;
  },
  saveMarks: async (id: number, cardId: number | undefined, markedNumbers: number[], autoMark?: boolean) => {
    const res = await apiClient.post<ApiResponse<void>>(`/games/${id}/marks`, { cardId, markedNumbers, autoMark });
    return res.data;
  },
  claim: async (id: number, cardId: number | undefined, markedNumbers?: number[], autoMark?: boolean) => {
    const res = await apiClient.post<ApiResponse<BingoClaimResultResponse>>(
      `/games/${id}/claim`,
      markedNumbers || autoMark !== undefined || cardId !== undefined ? { cardId, markedNumbers, autoMark } : undefined
    );
    return res.data;
  },
  getPendingClaims: async (gameId: number) => {
    const res = await apiClient.get<ApiResponse<BingoClaimResponse[]>>(`/games/${gameId}/claims/pending`);
    return res.data;
  },
  getPendingClaimCards: async (gameId: number) => {
    const res = await apiClient.get<ApiResponse<PendingClaimCard[]>>(`/games/${gameId}/claims/cards`);
    return res.data;
  },
  rejectClaim: async (gameId: number, claimId: number, reason?: string) => {
    const res = await apiClient.post<ApiResponse<string>>(
      `/games/${gameId}/claims/${claimId}/reject`,
      null,
      { params: { reason: reason ?? 'Rejected by admin' } }
    );
    return res.data;
  },
  approveClaim: async (gameId: number, claimId: number) => {
    const res = await apiClient.post<ApiResponse<BingoClaimResultResponse>>(
      `/games/${gameId}/claims/${claimId}/approve`
    );
    return res.data;
  },
  getState: async (id: number) => {
    const res = await apiClient.get<ApiResponse<GameStateResponse>>(`/games/${id}/state`);
    return res.data;
  },
  getAdminState: async (id: number) => {
    const res = await apiClient.get<ApiResponse<AdminGameStateResponse>>(`/games/${id}/state`);
    return res.data;
  },
  getFairness: async (id: number) => {
    const res = await apiClient.get<ApiResponse<FairnessProof>>(`/games/${id}/fairness`);
    return res.data;
  },
  approveAllClaims: async (id: number) => {
    const res = await apiClient.post<ApiResponse<BingoClaimResultResponse>>(`/games/${id}/claims/approve-all`);
    return res.data;
  },
  restartGame: async (id: number) => {
    const res = await apiClient.post<ApiResponse<AdminGameResponse>>(`/games/${id}/restart`);
    return res.data;
  },
  audit: async (id: number) => {
    const res = await apiClient.get<ApiResponse<AdminGameResponse>>(`/games/${id}/audit`);
    return res.data;
  },
  getHistory: async () => {
    const res = await apiClient.get<ApiResponse<AdminGameResponse[]>>('/games/history');
    return res.data;
  },
  getPrizeSuggestion: async (id: number) => {
    const res = await apiClient.get<ApiResponse<PrizeSuggestion>>(`/games/${id}/prize-suggestion`);
    return res.data;
  },
  getPlayerHistory: async () => {
    const res = await apiClient.get<ApiResponse<PlayerGameResponse[]>>('/games/player/history');
    return res.data;
  },
  getPlayerCardHistory: async () => {
    const res = await apiClient.get<ApiResponse<PlayerCardHistory[]>>('/games/player/history/cards');
    return res.data;
  },
  getAutomation: async () => {
    const res = await apiClient.get<ApiResponse<AutomationConfig>>('/automation');
    return res.data;
  },
  saveAutomation: async (data: AutomationConfigRequest) => {
    const res = await apiClient.put<ApiResponse<AutomationConfig>>('/automation', data);
    return res.data;
  },
};
