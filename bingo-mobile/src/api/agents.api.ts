import apiClient from './client';
import {
  ApiResponse,
  AgentResponse,
  AgentDeletion,
  AgentStatusRequest,
  AdminWarningResponse,
  AgentStatsResponse,
  OwnerFeeSettlementResponse,
  OwnerFeeSummaryResponse,
  OwnerFeeSettlementCreate,
  AdminOwnerFeeSummaryResponse,
} from '@/types';

export const agentsApi = {
  list: async () => {
    const res = await apiClient.get<ApiResponse<AgentResponse[]>>('/agents');
    return res.data;
  },
  invite: async () => {
    const res = await apiClient.post<ApiResponse<string>>('/agents/invite');
    return res.data;
  },
  updateStatus: async (id: number, data: AgentStatusRequest) => {
    const res = await apiClient.patch<ApiResponse<AgentResponse>>(`/agents/${id}/status`, data);
    return res.data;
  },
  /**
   * Delete an agent for good. The server refuses while a game is open or money is
   * pending, and answers with the scale of what was removed.
   */
  remove: async (id: number) => {
    const res = await apiClient.delete<ApiResponse<AgentDeletion>>(`/agents/${id}`);
    return res.data;
  },
  warn: async (id: number, reason: string) => {
    const res = await apiClient.post<ApiResponse<AdminWarningResponse>>(`/agents/${id}/warn`, { reason });
    return res.data;
  },
  getWarnings: async (id: number) => {
    const res = await apiClient.get<ApiResponse<AdminWarningResponse[]>>(`/agents/${id}/warnings`);
    return res.data;
  },
  getStats: async (id: number) => {
    const res = await apiClient.get<ApiResponse<AgentStatsResponse>>(`/agents/${id}/stats`);
    return res.data;
  },
  getFeeSummary: async () => {
    const res = await apiClient.get<ApiResponse<OwnerFeeSummaryResponse>>('/agents/fee-summary');
    return res.data;
  },
  getAllFeeSummary: async () => {
    const res = await apiClient.get<ApiResponse<AdminOwnerFeeSummaryResponse[]>>('/agents/fee-summary/all');
    return res.data;
  },
  createFeeSettlement: async (data: OwnerFeeSettlementCreate) => {
    const res = await apiClient.post<ApiResponse<OwnerFeeSettlementResponse>>('/agents/fee-settlements', data);
    return res.data;
  },
  getFeeSettlements: async () => {
    const res = await apiClient.get<ApiResponse<OwnerFeeSettlementResponse[]>>('/agents/fee-settlements');
    return res.data;
  },
  handleFeeSettlement: async (id: number, data: { action: string; reason?: string }) => {
    const res = await apiClient.patch<ApiResponse<string>>(`/agents/fee-settlements/${id}`, data);
    return res.data;
  },
};
