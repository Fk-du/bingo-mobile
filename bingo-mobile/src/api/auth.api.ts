import apiClient from './client';
import {
  ApiResponse,
  UserProfileResponse,
  DepositAccount,
  PhoneLoginRequest,
  PhoneAuthResponse,
  PasswordStatusResponse,
  PasswordStatusRequest,
  PasswordResetRequest,
  ConfirmPasswordResetRequest,
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
  requestPasswordReset: async (data: PasswordResetRequest) => {
    const res = await apiClient.post<ApiResponse<string>>('/auth/password/reset/request', data);
    return res.data;
  },
  confirmPasswordReset: async (data: ConfirmPasswordResetRequest) => {
    const res = await apiClient.post<ApiResponse<PhoneAuthResponse>>(
      '/auth/password/reset/confirm',
      data
    );
    return res.data;
  },
  registerPushToken: async (token: string) => {
    const res = await apiClient.post<ApiResponse<null>>('/auth/push-token', { token });
    return res.data;
  },
  me: async () => {
    const res = await apiClient.get<ApiResponse<UserProfileResponse>>('/users/me');
    return res.data;
  },
  updateProfile: async (data: {
    depositAccounts?: DepositAccount[];
    businessName?: string;
    preferredLanguage?: string;
  }) => {
    const res = await apiClient.put<ApiResponse<UserProfileResponse>>('/users/me', data);
    return res.data;
  },
};