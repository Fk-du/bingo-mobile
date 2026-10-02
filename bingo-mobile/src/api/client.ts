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

/**
 * Pick the most specific error text available.
 *
 * `errors.<code>` translations are a coarse bucket ("Wallet operation failed.").
 * When the server sent a `userMessage` that says more than its own `message` --
 * e.g. "Your balance is 4 coins, but this card costs 10 coins. You need 6 more
 * coins." -- that detail is what the player actually needs, so it wins. The
 * localized bucket is only used when the server had nothing extra to say, which
 * keeps localized wording for auth and validation failures.
 */
export function getApiErrorMessage(error: unknown): string {
  const e = error as { userMessage?: string; message?: string; code?: string };
  const userMessage = e?.userMessage?.trim();

  if (e?.code) {
    const localized = translateClientMessage(`errors.${e.code}`);
    const serverHadDetail = Boolean(userMessage) && userMessage !== e?.message;
    if (localized && !serverHadDetail) return localized;
  }
  return userMessage || e?.message || 'Something went wrong';
}

export default apiClient;