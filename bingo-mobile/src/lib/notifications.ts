import Constants from 'expo-constants';
import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { Platform } from 'react-native';
import { authApi } from '@/api/auth.api';

export const GAME_NOTIFICATION_CHANNEL_ID = 'games';

let handlerInstalled = false;
let tapHandlerInstalled = false;
let setupInFlight = false;

/**
 * Foreground notifications are dropped by default — the handler opts the app in
 * so a push that arrives while the app is open still shows a banner.
 */
function installNotificationHandler() {
  if (handlerInstalled || Platform.OS === 'web') return;
  handlerInstalled = true;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false,
    }),
  });
}

async function ensureGameChannel() {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(GAME_NOTIFICATION_CHANNEL_ID, {
    name: 'Game alerts',
    importance: Notifications.AndroidImportance.HIGH,
    vibrationPattern: [0, 250, 250, 250],
  });
}

async function hasNotificationPermission() {
  const existing = await Notifications.getPermissionsAsync();
  if (existing.granted) return true;
  if (existing.status === 'undetermined') {
    const requested = await Notifications.requestPermissionsAsync();
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
  installNotificationHandler();
  try {
    if (!(await hasNotificationPermission())) return;
    await ensureGameChannel();

    const projectId =
      Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
    if (!projectId) return;

    const { data: token } = await Notifications.getExpoPushTokenAsync({ projectId });
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

  await Notifications.scheduleNotificationAsync({
    content: {
      title: 'Game starting!',
      body: 'A game you joined is starting now — open the app to play.',
      data: { gameId },
    },
    trigger: {
      type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
      seconds: Math.max(1, Math.ceil((target - Date.now()) / 1000)),
      channelId: GAME_NOTIFICATION_CHANNEL_ID,
    },
  });
}

/**
 * Tapping a game-start notification deep-links straight into the game screen.
 */
export function installPushTapHandler() {
  if (Platform.OS === 'web' || tapHandlerInstalled) return;
  tapHandlerInstalled = true;

  const redirect = (notification: Notifications.Notification | null) => {
    const gameId = notification?.request.content.data?.gameId;
    if (typeof gameId === 'number') {
      router.push(`/(player)/game/${gameId}`);
    }
  };

  Notifications.getLastNotificationResponseAsync().then((response) => {
    if (response) redirect(response.notification);
  });

  Notifications.addNotificationResponseReceivedListener((response) => {
    redirect(response.notification);
  });
}