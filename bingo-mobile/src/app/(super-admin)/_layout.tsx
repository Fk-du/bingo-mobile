import { Stack, useRouter } from 'expo-router';
import { Alert, Pressable, Text, View } from 'react-native';
import { useAuthStore } from '@/store/auth.store';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { ThemeToggleButton } from '@/components/ui/ThemeToggleButton';

export default function SuperAdminLayout() {
  const t = useTranslate();
  const router = useRouter();
  const { colors } = useTheme();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  const handleLogout = () => {
    Alert.alert(
      t('common.logout') ?? 'Log out',
      t('common.logoutConfirm') ?? 'Are you sure you want to log out?',
      [
        { text: t('common.cancel') ?? 'Cancel', style: 'cancel' },
        {
          text: t('common.logout') ?? 'Log out',
          style: 'destructive',
          onPress: () => {
            logout();
            router.replace('/(auth)/login');
          },
        },
      ]
    );
  };

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
          headerTitle: t('super.dbTitle') ?? 'Platform Overview',
          headerRight: () => (
            <View className="flex-row items-center gap-2">
              <ThemeToggleButton />
              <Pressable onPress={handleLogout} className="px-3 py-1 rounded-full bg-bp-danger20">
                <Text className="text-bp-dangerInk text-sm">{t('common.logout') ?? 'Log out'}</Text>
              </Pressable>
            </View>
          ),
          headerLeft: () => (
            <Text className="text-bp-textPrimary font-bold">
              {user?.firstName ?? user?.username ?? ''}
            </Text>
          ),
        }}
      />
      <Stack.Screen name="reports" options={{ title: t('super.rpTitle') ?? 'Reports' }} />
      <Stack.Screen name="agents" options={{ title: t('super.agTitle') ?? 'Agent management' }} />
      <Stack.Screen name="cards" options={{ title: t('super.scTitle') ?? 'Card pool requests' }} />
      <Stack.Screen name="broadcast" options={{ title: t('super.sbTitle') ?? 'Send a message' }} />
      <Stack.Screen name="config" options={{ title: t('super.cfgTitle') ?? 'Platform settings' }} />
      <Stack.Screen name="owner-fees" options={{ title: t('super.sfTitle') ?? 'Agent fee ledger' }} />
      <Stack.Screen name="notifications" options={{ title: t('mobile.notifications') ?? 'Notifications' }} />
    </Stack>
  );
}