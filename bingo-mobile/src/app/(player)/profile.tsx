import { useQuery } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, ScrollView, Text, View } from 'react-native';
import { authApi, inviteApi } from '@/api';
import { Button, Card, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { IconMoon, IconSun } from '@/components/ui/icons';
import { getClientLocale, setClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { useAuthStore } from '@/store/auth.store';

const LOCALES = ['en', 'am'] as const;

export default function PlayerProfileScreen() {
  const t = useTranslate();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const logout = useAuthStore((s) => s.logout);
  const { isDark, colors, toggle: toggleTheme } = useTheme();
  const [copied, setCopied] = useState(false);
  const [showInvite, setShowInvite] = useState(false);
  const [showLanguage, setShowLanguage] = useState(false);

  const { data: inviteLink } = useQuery({
    queryKey: ['invite/link'],
    queryFn: () => inviteApi.getMyLink(),
  });
  const { data: inviteStats } = useQuery({
    queryKey: ['invite/stats'],
    queryFn: () => inviteApi.getMyStats(),
  });

  const link = inviteLink?.data;
  const stats = inviteStats?.data;

  const displayName =
    [user?.firstName, user?.lastName].filter(Boolean).join(' ') ||
    user?.username ||
    (t('player.playerLabel') ?? 'Player');

  const handleCopy = async () => {
    if (!link) return;
    await Clipboard.setStringAsync(link);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const switchLanguage = async (locale: 'en' | 'am') => {
    setClientLocale(locale);
    if (user) setUser({ ...user, preferredLanguage: locale });
    try {
      await authApi.updateProfile({ preferredLanguage: locale });
    } catch {
      // language switch still applies locally even if sync fails
    }
  };

  const handleLogout = () => {
    Alert.alert(
      t('player.logOut') ?? 'Log out',
      t('common.logoutConfirm') ?? 'Are you sure you want to log out?',
      [
        { text: t('common.cancel') ?? 'Cancel', style: 'cancel' },
        {
          text: t('player.logOut') ?? 'Log out',
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
    <Screen>
      <ScreenHeader title={t('player.profileLabel') ?? 'Profile'} />

      <ScrollView contentContainerClassName="gap-3 pb-8">
        <Card className="items-center gap-1">
          <View className="h-20 w-20 rounded-full bg-bp-primary20 border-2 border-bp-primary items-center justify-center">
            <Text className="text-bp-primary text-3xl font-black">
              {displayName.slice(0, 1).toUpperCase() || '🎱'}
            </Text>
          </View>
          <Text className="text-bp-textPrimary text-xl font-bold">{displayName}</Text>
          <Text className="text-bp-textSecondary text-sm">{t('player.playerLabel') ?? 'Player'}</Text>
          {user?.username ? <Text className="text-bp-textSecondary text-xs">@{user.username}</Text> : null}
          {user?.phoneNumber ? <Text className="text-bp-textSecondary text-xs">📞 {user.phoneNumber}</Text> : null}
        </Card>

        {/* The home screen redirects straight to the game, so the theme switch
            lives on the profile instead of a header the player rarely sees.
            Both icons stay visible and the active one is outlined in the
            primary colour, so the current mode reads at a glance. */}
        <Card className="flex-row items-center justify-between">
          <Text className="text-bp-textPrimary font-semibold">
            {t('player.appearance') ?? 'Appearance'}
          </Text>
          <View className="flex-row items-center gap-2">
            <ModeToggle
              active={!isDark}
              label={t('player.lightMode') ?? 'Light'}
              onPress={() => !isDark && toggleTheme()}
              borderColor={!isDark ? colors.primary : colors.borderInactive}
            >
              <IconSun size={18} color={!isDark ? colors.primary : colors.textInactive} />
            </ModeToggle>
            <ModeToggle
              active={isDark}
              label={t('player.darkMode') ?? 'Dark'}
              onPress={() => isDark && toggleTheme()}
              borderColor={isDark ? colors.primary : colors.borderInactive}
            >
              <IconMoon size={18} color={isDark ? colors.primary : colors.textInactive} />
            </ModeToggle>
          </View>
        </Card>

        <Pressable onPress={() => setShowInvite((v) => !v)} className="active:opacity-80">
          <Card className="flex-row justify-between items-center">
            <Text className="text-bp-textPrimary font-semibold">{t('player.inviteFriends') ?? 'Invite friends'}</Text>
            <Text className="text-bp-primary">{showInvite ? '−' : '›'}</Text>
          </Card>
        </Pressable>

        {showInvite && (
          <Card className="gap-2">
            <Text className="text-bp-goldInk font-semibold">{t('player.inviteFriends') ?? 'Invite friends'}</Text>
            <Text className="text-bp-textSecondary text-xs">{t('player.shareYourLink') ?? 'Share your link'}</Text>
            {stats && (
              <View className="flex-row gap-2 mt-1">
                <View className="flex-1 rounded-xl bg-bp-surfaceAlt px-3 py-2 items-center">
                  <Text className="text-bp-textPrimary text-lg font-bold">{stats.totalRegistrations ?? 0}</Text>
                  <Text className="text-bp-textSecondary text-[10px]">{t('player.friendsJoined') ?? 'Friends joined'}</Text>
                </View>
                <View className="flex-1 rounded-xl bg-bp-surfaceAlt px-3 py-2 items-center">
                  <Text className="text-bp-textPrimary text-lg font-bold">{stats.activeCodes ?? 0}</Text>
                  <Text className="text-bp-textSecondary text-[10px]">{t('player.activeLinks') ?? 'Active links'}</Text>
                </View>
              </View>
            )}
            {link ? (
              <View className="rounded-xl bg-bp-surfaceAlt border border-bp-borderInactive px-3 py-2">
                <Text className="text-bp-textSecondary text-xs" numberOfLines={3}>
                  {link}
                </Text>
              </View>
            ) : null}
            <View className="flex-row gap-2">
              <Button
                variant="primary"
                disabled={!link}
                onPress={() => void handleCopy()}
                style={{ flex: 1 }}
              >
                {copied ? t('player.copied') ?? 'Copied!' : t('player.copyLink') ?? 'Copy link'}
              </Button>
              <Button
                variant="outline"
                disabled={!link}
                onPress={() => {
                  if (!link) return;
                  void Clipboard.setStringAsync(link);
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                }}
                style={{ flex: 1 }}
              >
                {t('player.share') ?? 'Share'}
              </Button>
            </View>
          </Card>
        )}

        <Pressable
          onPress={() => router.push('/(player)/history')}
          className="active:opacity-80"
        >
          <Card className="flex-row justify-between items-center">
            <Text className="text-bp-textPrimary font-semibold">{t('player.gameHistory') ?? 'Game history'}</Text>
            <Text className="text-bp-primary">›</Text>
          </Card>
        </Pressable>

        <Pressable onPress={() => setShowLanguage((v) => !v)} className="active:opacity-80">
          <Card className="flex-row justify-between items-center">
            <Text className="text-bp-textPrimary font-semibold">{t('player.language') ?? 'Language'}</Text>
            <Text className="text-bp-primary">{showLanguage ? '−' : '›'}</Text>
          </Card>
        </Pressable>

        {showLanguage && (
          <Card>
            <View className="flex-row gap-2">
              {LOCALES.map((locale) => {
                const active = locale === getClientLocale();
                return (
                  <Pressable
                    key={locale}
                    onPress={() => void switchLanguage(locale)}
                    className={`flex-1 rounded-full border px-4 py-2 items-center ${
                      active ? 'border-bp-primary bg-bp-primary20' : 'border-bp-borderInactive'
                    }`}
                  >
                    <Text className={active ? 'text-bp-textPrimary font-semibold' : 'text-bp-textSecondary'}>
                      {locale === 'en' ? (t('player.english') ?? 'English') : (t('player.amharic') ?? 'አማርኛ')}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </Card>
        )}

        <Button variant="ghost" onPress={handleLogout} style={{ marginTop: 12 }}>
          <Text className="text-bp-dangerInk">{t('player.logOut') ?? 'Log out'}</Text>
        </Button>
      </ScrollView>
    </Screen>
  );
}

/** One light/dark option. Only the active one is outlined and coloured. */
function ModeToggle({
  active,
  label,
  onPress,
  borderColor,
  children,
}: {
  active: boolean;
  label: string;
  onPress: () => void;
  borderColor: string;
  children: React.ReactNode;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      accessibilityLabel={label}
      // The inactive half stays tappable: tapping it switches to that mode
      // rather than doing nothing, which is what a segmented control implies.
      className="h-9 w-9 items-center justify-center rounded-full border bg-bp-surface"
      style={{ borderColor, opacity: active ? 1 : 0.7 }}
    >
      {children}
    </Pressable>
  );
}