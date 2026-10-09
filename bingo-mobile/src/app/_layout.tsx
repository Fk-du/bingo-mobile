import '@/global.css';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack, useRouter, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { StatusBar as NativeStatusBar, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Role } from '@/types';
import { useAuthStore } from '@/store/auth.store';
import { setClientLocale } from '@/lib/clientTranslations';
import { installPushTapHandler, setupPushNotifications } from '@/lib/notifications';
import { ThemeProvider, useTheme } from '@/lib/theme';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: 1,
    },
  },
});

function AppRoutes() {
  const { colors } = useTheme();
  const { isAuthenticated, user } = useAuthStore();
  const router = useRouter();
  const segments = useSegments();

  useEffect(() => {
    if (user?.preferredLanguage) {
      setClientLocale(user.preferredLanguage);
    }
  }, [user?.preferredLanguage]);

  useEffect(() => {
    installPushTapHandler();
    if (isAuthenticated) {
      void setupPushNotifications();
    }
  }, [isAuthenticated]);

  useEffect(() => {
    const inAuthGroup = segments[0] === '(auth)';
    const inPlayerGroup = segments[0] === '(player)';
    const inAdminGroup = segments[0] === '(admin)';
    const inSuperAdminGroup = segments[0] === '(super-admin)';

    if (!isAuthenticated) {
      if (!inAuthGroup) {
        router.replace('/(auth)/login');
      }
      return;
    }

    if (user?.role === Role.ADMIN) {
      if (inPlayerGroup || inSuperAdminGroup) router.replace('/(admin)');
      return;
    }

    if (user?.role === Role.SUPER_ADMIN) {
      if (inPlayerGroup || inAdminGroup) router.replace('/(super-admin)');
      return;
    }

    // PLAYER: only the player group is reachable
    if (inAdminGroup || inSuperAdminGroup) router.replace('/(player)');
  }, [isAuthenticated, user?.role, segments, router]);

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: 'transparent' },
        }}
      >
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(player)" />
        <Stack.Screen name="(admin)" />
        <Stack.Screen name="(super-admin)" />
      </Stack>
    </View>
  );
}

export default function RootLayout() {
  return (
    <ThemeProvider>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <AppThemeStatusBar />
          <AppRoutes />
        </QueryClientProvider>
      </SafeAreaProvider>
    </ThemeProvider>
  );
}

function AppThemeStatusBar() {
  const { isDark, colors } = useTheme();

  // The clock/battery strip is drawn by the OS or browser, not the app, so the
  // style prop alone is not enough: a phone in system dark over the light theme
  // left white icons on the near-white page. theme-color is what Android Chrome
  // paints the status bar (and toolbar) with — pinned to the app background the
  // bar and its icons always contrast, in both themes.
  useEffect(() => {
    if (typeof document === 'undefined') return;
    let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'theme-color');
      document.head.appendChild(meta);
    }
    meta.setAttribute('content', colors.background);
  }, [colors.background]);

  return (
    <>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      {/* Android native: the window's status bar keeps its static theme color
          otherwise, which clashes with the dark icons of the light theme. */}
      <NativeStatusBar backgroundColor={colors.background} />
    </>
  );
}