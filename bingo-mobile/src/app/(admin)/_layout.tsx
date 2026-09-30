import { Stack } from 'expo-router';
import { Pressable, Text, View } from 'react-native';
import { useAuthStore } from '@/store/auth.store';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { ThemeToggleButton } from '@/components/ui/ThemeToggleButton';

export default function AdminLayout() {
  const t = useTranslate();
  const { colors } = useTheme();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.textInactive,
        headerTitleStyle: { color: colors.textPrimary },
      }}
    >
      <Stack.Screen
        name="index"
        options={{
          headerTitle: '',
          headerRight: () => (
            <View className="flex-row items-center gap-2">
              <ThemeToggleButton />
              <Pressable onPress={() => void logout()} className="px-3 py-1 rounded-full bg-bp-danger20">
                <Text className="text-bp-dangerInk text-sm">{t('common.logout') ?? 'Log out'}</Text>
              </Pressable>
            </View>
          ),
          headerLeft: () => (
            <Text className="text-bp-textPrimary font-bold">
              {t('admin.welcomeBack') ?? 'Welcome back,'} {user?.firstName ?? ''}
            </Text>
          ),
        }}
      />
      <Stack.Screen
        name="game/[id]"
        options={{
          title: t('admin.adgEyebrow') ?? 'Live game',
          headerBackTitle: t('common.back') ?? 'Back',
        }}
      />
      <Stack.Screen name="players" options={{ title: t('admin.playersTitle') ?? 'Players' }} />
      <Stack.Screen name="cards" options={{ title: t('admin.cardsTitle') ?? 'Card pool' }} />
      <Stack.Screen name="coins" options={{ title: t('admin.topUpTitle') ?? 'Top-up approvals' }} />
      <Stack.Screen name="withdrawals" options={{ title: t('admin.wdTitle') ?? 'Payout requests' }} />
      <Stack.Screen name="profile" options={{ title: t('admin.profileTitle') ?? 'Profile' }} />
      <Stack.Screen name="new-game" options={{ title: t('admin.createGame') ?? 'New game' }} />
      <Stack.Screen name="broadcast" options={{ title: t('admin.broadcastTitle') ?? 'Broadcast' }} />
      <Stack.Screen name="owner-fees" options={{ title: t('admin.ofTitle') ?? 'Owner fees' }} />
      <Stack.Screen name="notifications" options={{ title: t('mobile.notifications') ?? 'Notifications' }} />
    </Stack>
  );
}