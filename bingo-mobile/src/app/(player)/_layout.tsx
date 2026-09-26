import { useQuery } from '@tanstack/react-query';
import { Tabs } from 'expo-router';
import { notificationsApi } from '@/api';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { IconBell, IconHistory, IconLobby, IconProfile, IconWallet } from '@/components/ui/icons';

export default function PlayerLayout() {
  const t = useTranslate();
  const { colors } = useTheme();
  const label = (key: string, fallback: string) => t(key) ?? fallback;

  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationsApi.unreadCount(),
    refetchInterval: 30_000,
  });
  const unread = unreadData?.data.count ?? 0;

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.borderInactive },
        tabBarActiveTintColor: '#6B5BFF',
        tabBarInactiveTintColor: colors.textInactive,
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: label('mobile.navLobby', 'Lobby'),
          tabBarIcon: ({ color }) => <IconLobby color={color} size={20} />,
        }}
      />
      <Tabs.Screen
        name="history"
        options={{
          title: label('mobile.navHistory', 'History'),
          tabBarIcon: ({ color }) => <IconHistory color={color} size={20} />,
        }}
      />
      <Tabs.Screen
        name="wallet"
        options={{
          title: label('mobile.navWallet', 'Wallet'),
          tabBarIcon: ({ color }) => <IconWallet color={color} size={20} />,
        }}
      />
      <Tabs.Screen
        name="notifications"
        options={{
          title: label('mobile.notifications', 'Alerts'),
          tabBarIcon: ({ color }) => <IconBell color={color} size={20} />,
          tabBarBadge: unread > 0 ? unread : undefined,
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: label('mobile.navProfile', 'Profile'),
          tabBarIcon: ({ color }) => <IconProfile color={color} size={20} />,
        }}
      />
      <Tabs.Screen name="game/[id]" options={{ href: null }} />
      <Tabs.Screen name="my-games" options={{ href: null }} />
      <Tabs.Screen name="withdraw" options={{ href: null }} />
    </Tabs>
  );
}