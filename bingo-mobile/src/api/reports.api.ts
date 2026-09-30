import apiClient from './client';
import { ApiResponse, DashboardSummaryResponse, AdminGameResponse, RevenueReportResponse } from '@/types';

export const reportsApi = {
  revenue: async () => {
    const res = await apiClient.get<ApiResponse<RevenueReportResponse>>('/reports/revenue');
    return res.data;
  },
  games: async () => {
    const res = await apiClient.get<ApiResponse<AdminGameResponse[]>>('/reports/games');
    return res.data;
  },
  dashboard: async () => {
    const res = await apiClient.get<ApiResponse<DashboardSummaryResponse>>('/reports/dashboard');
    return res.data;
  },
};
