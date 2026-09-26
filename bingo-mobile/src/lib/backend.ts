import Constants from 'expo-constants';
import { Platform } from 'react-native';

const DEFAULT_BACKEND_URL = 'http://localhost:8080';

/**
 * Backend base URL. Override via app config extra `backendUrl` (e.g.
 * EXPO_PUBLIC_BACKEND_URL in .env). On a physical device, `localhost` points at
 * the phone, so set EXPO_PUBLIC_BACKEND_URL to the dev machine's LAN IP.
 */
export function getBackendUrl(): string {
  const extra = Constants.expoConfig?.extra as { backendUrl?: string } | undefined;
  const raw = extra?.backendUrl ?? process.env.EXPO_PUBLIC_BACKEND_URL;
  if (raw === undefined || raw === '') return DEFAULT_BACKEND_URL;
  return normalizeUrl(raw);
}

/**
 * Effective backend base for this platform. On an Android emulator, `localhost`
 * points at the device itself, so API and WS both translate it to the host's
 * emulator alias 10.0.2.2. A real device must set EXPO_PUBLIC_BACKEND_URL to
 * the dev machine's LAN IP.
 */
export function getEffectiveBackendUrl(): string {
  let base = getBackendUrl();
  if (Platform.OS === 'android' && base.includes('localhost')) {
    base = base.replace('localhost', '10.0.2.2');
  }
  return base;
}

export function getApiBaseUrl(): string {
  return `${getEffectiveBackendUrl()}/api/v1`;
}

/**
 * WebSocket URL. Android emulator reaches the host via 10.0.2.2; iOS simulator
 * and web can use localhost. Raw WebSocket (no SockJS) — STOMP only.
 */
export function getWsBaseUrl(): string {
  const base = getEffectiveBackendUrl();
  const wsScheme = base.startsWith('https') ? 'wss' : 'ws';
  return `${wsScheme}://${base.replace(/^https?:\/\//, '')}/ws`;
}

function normalizeUrl(url: string): string {
  return url.replace(/\/+$/, '');
}