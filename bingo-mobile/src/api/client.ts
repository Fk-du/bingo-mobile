import { create as createHttp } from 'axios';
import { getApiBaseUrl } from '@/lib/backend';
import { tokenStorage } from '@/store/tokenStorage';
import { useAuthStore } from '@/store/auth.store';
import { translateClientMessage } from '@/lib/clientTranslations';

const apiClient = createHttp({
  baseURL: getApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(async (config) => {
  const token = await tokenStorage.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type ErrorBody = { message?: string; userMessage?: string; code?: string };

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error.response?.data as ErrorBody | undefined;

    error.code = body?.code ?? null;
    error.userMessage =
      body?.userMessage ?? body?.message ?? error.message ?? 'Something went wrong';

    // 401 = invalid/expired JWT → force re-login. 421 (no_password) and 403
    // (suspended/pending) are handled by the calling screens, not here.
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  }
);

export function getApiErrorMessage(error: unknown): string {
  const e = error as { userMessage?: string; code?: string };
  if (e?.code) {
    const localized = translateClientMessage(`errors.${e.code}`);
    if (localized) return localized;
  }
  return e?.userMessage ?? 'Something went wrong';
}

export default apiClient;