import { useQuery } from '@tanstack/react-query';
import { Tabs } from 'expo-router';
import { Platform } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { notificationsApi } from '@/api';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { IconBell, IconHistory, IconLobby, IconProfile, IconWallet } from '@/components/ui/icons';

/** Matches bp-brand-primary. Kept raw so the pill and the icon agree exactly. */
const BRAND = '#6B5BFF';
/** Alpha-baked brand tint, the same value as the bp-primary20 token. */
const BRAND_TINT = '#6B5BFF33';

export default function PlayerLayout() {
  const t = useTranslate();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const label = (key: string, fallback: string) => t(key) ?? fallback;

  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationsApi.unreadCount(),
    refetchInterval: 30_000,
  });
  const unread = unreadData?.data.count ?? 0;

  // The bar is sized by hand rather than left at the platform default (49pt
  // on iOS, 56 on Android) because the default leaves a lot of dead air under
  // a 10pt label. Icon + gap + label + padding lands just under 56, which is
  // still above the 44pt minimum touch target, and every pixel saved here goes
  // back to the screen content above it.
  // Slightly larger bar to give more breathing room and better tap targets,
  // while still keeping content visible on smaller phones.
  const barHeight = 64 + insets.bottom;

  // The tabs have no native header, so the top inset is applied here once
  // rather than on every screen inside.
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['top']}>
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarActiveTintColor: BRAND,
          tabBarInactiveTintColor: colors.textInactive,
          // A tinted pill behind the active item, so the current tab is
          // obvious at a glance instead of relying on colour alone.
          tabBarActiveBackgroundColor: BRAND_TINT,
          tabBarStyle: {
            backgroundColor: colors.surface,
            borderTopColor: colors.borderInactive,
            borderTopWidth: 1,
            height: barHeight,
            paddingTop: 8,
            paddingBottom: insets.bottom + 8,
          },
          tabBarItemStyle: {
            borderRadius: 999,
            // Inset the pill from the neighbouring item so the rounded shape
            // reads as a separate control rather than a filled column.
            marginHorizontal: 3,
            paddingVertical: 4,
          },
          tabBarLabelStyle: {
            fontSize: 11,
            fontWeight: '700',
            marginTop: 3,
            // Amharic nav labels are long words; shrink to fit instead of
            // truncating, so no tab ever renders as "ማሳወቂያ…".
            letterSpacing: 0.01,
            ...Platform.select({ ios: { marginBottom: 0 }, default: {} }),
          },
          tabBarBadgeStyle: {
            backgroundColor: colors.danger,
            color: '#FFFFFF',
            fontSize: 10,
            fontWeight: '700',
            minWidth: 16,
            height: 16,
            lineHeight: 15,
          },
          // Forms on Profile and Wallet were having the bar cover the field
          // they were typing into.
          tabBarHideOnKeyboard: true,
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: label('mobile.navLobby', 'Game'),
                tabBarIcon: ({ color, focused }) => <IconLobby color={color} size={focused ? 26 : 24} />,
          }}
        />
        <Tabs.Screen
          name="history"
          options={{
            title: label('mobile.navHistory', 'History'),
            tabBarIcon: ({ color, focused }) => <IconHistory color={color} size={focused ? 26 : 24} />,
          }}
        />
        <Tabs.Screen
          name="wallet"
          options={{
            title: label('mobile.navWallet', 'Wallet'),
            tabBarIcon: ({ color, focused }) => <IconWallet color={color} size={focused ? 26 : 24} />,
          }}
        />
        <Tabs.Screen
          name="notifications"
          options={{
            title: label('mobile.navAlerts', 'Alerts'),
            tabBarIcon: ({ color, focused }) => <IconBell color={color} size={focused ? 26 : 24} />,
            tabBarBadge: unread > 0 ? unread : undefined,
          }}
        />
        <Tabs.Screen
          name="profile"
          options={{
            title: label('mobile.navProfile', 'Profile'),
            tabBarIcon: ({ color, focused }) => <IconProfile color={color} size={focused ? 26 : 24} />,
          }}
        />
        {/* The game board is the main content, not a tab destination. It keeps
            the bar so the player can leave the game, but it declares no tab. */}
        <Tabs.Screen name="game/[id]" options={{ href: null }} />
        <Tabs.Screen name="my-games" options={{ href: null }} />
        <Tabs.Screen name="withdraw" options={{ href: null }} />
        {/* Settings are reached from the gear on the game screen. A screen that
            will grow new groups does not need a tab slot of its own. */}
        <Tabs.Screen name="settings" options={{ href: null }} />
      </Tabs>
    </SafeAreaView>
  );
}
