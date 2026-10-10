import Constants, { ExecutionEnvironment } from 'expo-constants';
import { router } from 'expo-router';
import { Platform } from 'react-native';
import { authApi } from '@/api/auth.api';
import type { Notification } from 'expo-notifications';

export const GAME_NOTIFICATION_CHANNEL_ID = 'games';

let handlerInstalled = false;
let tapHandlerInstalled = false;
let setupInFlight = false;
let moduleCache: typeof import('expo-notifications') | null = null;
let moduleChecked = false;

/**
 * expo-notifications throws at import time on Android inside Expo Go (SDK 53+),
 * where push was removed. It is only resolved lazily — any runtime that cannot
 * load it makes every function here a no-op instead of crashing the router tree.
 */
function expoNotifications(): typeof import('expo-notifications') | null {
  if (moduleChecked) return moduleCache;
  // Expo Go on Android cannot host expo-notifications at all (SDK 53+) — the
  // module fails during evaluation in a way a synchronous try/catch around a
  // require cannot contain, so the module is never touched there. Every
  // function here becomes a no-op rather than crashing the router tree.
  if (Platform.OS === 'android' && Constants.executionEnvironment === ExecutionEnvironment.StoreClient) {
    moduleCache = null;
  } else {
    try {
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      moduleCache = require('expo-notifications');
    } catch (e) {
      console.warn('Push notifications are unavailable in this runtime', e);
      moduleCache = null;
    }
  }
  moduleChecked = true;
  return moduleCache;
}

/**
 * Foreground notifications are dropped by default — the handler opts the app in
 * so a push that arrives while the app is open still shows a banner.
 */
function installNotificationHandler() {
  const N = expoNotifications();
  if (handlerInstalled || !N) return;
  handlerInstalled = true;
  N.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false,
    }),
  });
}

async function ensureGameChannel() {
  const N = expoNotifications();
  if (Platform.OS !== 'android' || !N) return;
  await N.setNotificationChannelAsync(GAME_NOTIFICATION_CHANNEL_ID, {
    name: 'Game alerts',
    importance: N.AndroidImportance.HIGH,
    vibrationPattern: [0, 250, 250, 250],
  });
}

async function hasNotificationPermission() {
  const N = expoNotifications();
  if (!N) return false;
  const existing = await N.getPermissionsAsync();
  if (existing.granted) return true;
  if (existing.status === 'undetermined') {
    const requested = await N.requestPermissionsAsync();
    return requested.granted;
  }
  return false;
}

/**
 * One-time wiring used right after login: enable the foreground handler, ask
 * for permission, create the Android channel, and store this device's Expo push
 * token on the backend so the server can ring the phone when a game starts.
 */
export async function setupPushNotifications(): Promise<void> {
  if (Platform.OS === 'web' || setupInFlight) return;
  setupInFlight = true;
  try {
    installNotificationHandler();
    if (!(await hasNotificationPermission())) return;
    await ensureGameChannel();

    const projectId =
      Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
    if (!projectId) return;

    const N = expoNotifications();
    if (!N) return;
    const { data: token } = await N.getExpoPushTokenAsync({ projectId });
    if (token) {
      await authApi.registerPushToken(token);
    }
  } catch (e) {
    console.warn('Could not register push token', e);
  } finally {
    setupInFlight = false;
  }
}

/**
 * A game this user joined is counting down. Schedules a local notification for
 * when it actually starts, so a player who backgrounded (or was killed mid-way)
 * after seeing the countdown still gets the nudge. This is also what covers
 * Expo Go, where remote push does not run.
 */
export async function scheduleGameStartLocalNotification(
  startIso: string | null | undefined,
  gameId: number
): Promise<void> {
  if (Platform.OS === 'web' || !startIso) return;
  const target = new Date(startIso).getTime();
  if (!Number.isFinite(target) || target <= Date.now()) return;
  if (!(await hasNotificationPermission())) return;

  const N = expoNotifications();
  if (!N) return;
  await N.scheduleNotificationAsync({
    content: {
      title: 'Game starting!',
      body: 'A game you joined is starting now — open the app to play.',
      data: { gameId },
    },
    trigger: {
      type: N.SchedulableTriggerInputTypes.TIME_INTERVAL,
      seconds: Math.max(1, Math.ceil((target - Date.now()) / 1000)),
      channelId: GAME_NOTIFICATION_CHANNEL_ID,
    },
  });
}

/**
 * Tapping a game-start notification deep-links straight into the game screen.
 */
export function installPushTapHandler() {
  const N = expoNotifications();
  if (!N || tapHandlerInstalled) return;
  tapHandlerInstalled = true;

  const redirect = (notification: Notification | null) => {
    const gameId = notification?.request.content.data?.gameId;
    if (typeof gameId === 'number') {
      router.push(`/(player)/game/${gameId}`);
    }
  };

  N.getLastNotificationResponseAsync().then((response) => {
    if (response) redirect(response.notification);
  });

  N.addNotificationResponseReceivedListener((response) => {
    redirect(response.notification);
  });
}