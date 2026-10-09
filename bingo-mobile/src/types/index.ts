import { UserProfileResponse } from './auth';

export * from './enums';
export * from './api';
export * from './auth';
export * from './agent';
export * from './game';
export * from './card';
export * from './player';
export * from './coin';
export * from './withdrawal';
export * from './transaction';
export * from './audit';
export * from './report';
export * from './notification';

export interface BroadcastRequest {
  target: string;
  message: string;
}

export interface ConfigUpdateRequest {
  config: Record<string, unknown>;
}

// ---- Mobile (phone + password) auth ----

export interface PhoneLoginRequest {
  phone: string;
  password: string;
}

export interface PhoneAuthResponse {
  jwt: string;
  user: UserProfileResponse;
}

export interface PasswordStatusRequest {
  phone: string;
}

export interface PasswordStatusResponse {
  hasPassword: boolean;
}

export interface PasswordResetRequest {
  phone: string;
}

export interface ConfirmPasswordResetRequest {
  phone: string;
  code: string;
  newPassword: string;
}

/** HTTP 421 body when the account has no password yet. */
export interface NoPasswordErrorBody {
  code: 'no_password';
  message: string;
  userMessage: string;
  status: number;
}
