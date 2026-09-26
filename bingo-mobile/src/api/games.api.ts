import apiClient from './client';
import {
  AdminGameStateResponse,
  ApiResponse,
  GameResponse,
  GameStateResponse,
  BingoClaimResponse,
  BingoClaimResultResponse,
  PendingClaimCard,
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
    const res = await apiClient.post<ApiResponse<GameResponse>>('/games', data);
    return res.data;
  },
  updateSettings: async (id: number, data: GameSettingsUpdateRequest) => {
    const res = await apiClient.patch<ApiResponse<GameResponse>>(`/games/${id}/settings`, data);
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
    const res = await apiClient.post<ApiResponse<GameResponse>>(`/games/${id}/start`);
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
    const res = await apiClient.get<ApiResponse<GameResponse[]>>('/games/active');
    return res.data;
  },
  register: async (id: number, count: number) => {
    const res = await apiClient.post<ApiResponse<RegisterResponse>>(`/games/${id}/register`, { count });
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
    const res = await apiClient.post<ApiResponse<GameResponse>>(`/games/${id}/restart`);
    return res.data;
  },
  audit: async (id: number) => {
    const res = await apiClient.get<ApiResponse<GameResponse>>(`/games/${id}/audit`);
    return res.data;
  },
  getHistory: async () => {
    const res = await apiClient.get<ApiResponse<GameResponse[]>>('/games/history');
    return res.data;
  },
  getPlayerHistory: async () => {
    const res = await apiClient.get<ApiResponse<GameResponse[]>>('/games/player/history');
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
