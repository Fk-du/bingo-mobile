import apiClient from './client';
import {
  ApiResponse,
  UserProfileResponse,
  PhoneLoginRequest,
  PhoneAuthResponse,
  PasswordStatusResponse,
  PasswordStatusRequest,
} from '@/types';

export const authApi = {
  phoneLogin: async (data: PhoneLoginRequest) => {
    const res = await apiClient.post<ApiResponse<PhoneAuthResponse>>('/auth/phone/login', data);
    return res.data;
  },
  passwordStatus: async (data: PasswordStatusRequest) => {
    const res = await apiClient.post<ApiResponse<PasswordStatusResponse>>('/auth/password/status', data);
    return res.data.data;
  },
  me: async () => {
    const res = await apiClient.get<ApiResponse<UserProfileResponse>>('/users/me');
    return res.data;
  },
  updateProfile: async (data: {
    depositAccountInfo?: string;
    businessName?: string;
    preferredLanguage?: string;
  }) => {
    const res = await apiClient.put<ApiResponse<UserProfileResponse>>('/users/me', data);
    return res.data;
  },
};