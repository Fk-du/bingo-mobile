import apiClient from './client';
import { ApiResponse } from '@/types';

export const inviteApi = {
  getMyLink: async () => {
    const res = await apiClient.get<ApiResponse<string>>('/invite/me');
    return res.data;
  },
  getMyStats: async () => {
    const res = await apiClient.get<ApiResponse<{ totalCodes: number; activeCodes: number; usedCodes: number; totalRegistrations: number }>>('/invite/me/stats');
    return res.data;
  },
};
