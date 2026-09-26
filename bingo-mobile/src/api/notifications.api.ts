import apiClient from './client';
import { ApiResponse, AppNotification } from '@/types';

export const notificationsApi = {
  list: async (limit = 50) => {
    const res = await apiClient.get<ApiResponse<AppNotification[]>>('/notifications', {
      params: { limit },
    });
    return res.data;
  },
  unreadCount: async () => {
    const res = await apiClient.get<ApiResponse<{ count: number }>>(
      '/notifications/unread-count'
    );
    return res.data;
  },
  markRead: async (id: number) => {
    const res = await apiClient.patch<ApiResponse<null>>(`/notifications/${id}/read`);
    return res.data;
  },
  markAllRead: async () => {
    const res = await apiClient.post<ApiResponse<{ count: number }>>(
      '/notifications/read-all'
    );
    return res.data;
  },
};