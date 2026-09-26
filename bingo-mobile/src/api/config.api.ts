import apiClient from './client';
import { ApiResponse, ConfigUpdateRequest } from '@/types';

export const configApi = {
  get: async () => {
    const res = await apiClient.get<ApiResponse<Record<string, unknown>>>('/config');
    return res.data;
  },
  /** Public, unauthenticated config (registration bot username for the mobile app). */
  publicInfo: async () => {
    const res = await apiClient.get<ApiResponse<Record<string, unknown>>>('/auth/public-config');
    return res.data;
  },
  update: async (data: ConfigUpdateRequest) => {
    const res = await apiClient.patch<ApiResponse<string>>('/config', data);
    return res.data;
  },
};
