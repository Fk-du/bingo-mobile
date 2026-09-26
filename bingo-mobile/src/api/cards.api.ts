import apiClient from './client';
import {
  ApiResponse,
  CardPoolResponse,
  CardRequestResponse,
  CardResponse,
} from '@/types';

export const cardsApi = {
  getAvailable: async (page = 1, size = 100) => {
    const res = await apiClient.get<ApiResponse<CardPoolResponse>>('/cards/available', {
      params: { page, size },
    });
    return res.data;
  },
  getById: async (id: number) => {
    const res = await apiClient.get<ApiResponse<CardResponse>>(`/cards/${id}`);
    return res.data;
  },
  createCardRequest: async (quantity: number) => {
    const res = await apiClient.post<ApiResponse<CardRequestResponse>>('/cards/requests', { quantity });
    return res.data;
  },
  getCardRequests: async () => {
    const res = await apiClient.get<ApiResponse<CardRequestResponse[]>>('/cards/requests');
    return res.data;
  },
  processCardRequest: async (id: number, data: { action: string; reason?: string }) => {
    const res = await apiClient.patch<ApiResponse<string>>(`/cards/requests/${id}`, data);
    return res.data;
  },
};