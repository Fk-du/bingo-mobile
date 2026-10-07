import { useQuery } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, ScrollView, Text, View } from 'react-native';
import { authApi, inviteApi, notificationsApi, walletApi } from '@/api';
import { Button, Card, Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import {
  IconBell,
  IconHistory,
  IconLobby,
  IconMoon,
  IconSun,
  IconWallet,
} from '@/components/ui/icons';
import { getClientLocale, setClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { useAuthStore } from '@/store/auth.store';

const LOCALES = ['en', 'am', 'ti'] as const;

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

  // The wallet row shows the balance inline and the alerts row shows the
  // unread count. Both used to live on tab icons, and losing them here would
  // mean opening a screen just to check a number.
  const { data: walletQuery } = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });
  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationsApi.unreadCount(),
  });
  const unread = unreadData?.data.count ?? 0;
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

  const switchLanguage = async (locale: 'en' | 'am' | 'ti') => {
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
      <ScreenHeader
        title={t('player.profileLabel') ?? 'Profile'}
        left={<ScreenBackButton />}
      />

      <ScrollView contentContainerClassName="gap-3 pb-8">
        <Card className="items-start gap-1">
          <View className="flex-row items-center gap-2">
            <Text className="text-xs" style={{ color: colors.textSecondary }}>{t('player.phoneLabel') ?? 'Phone'}</Text>
            <Text className="text-xs" style={{ color: colors.textPrimary }}>
              {user?.phoneNumber ? user.phoneNumber : (displayName || (t('player.playerLabel') ?? 'Player'))}
            </Text>
          </View>
        </Card>

        {/* The home screen redirects straight to the game, so the theme switch
            lives on the profile instead of a header the player rarely sees.
            Only the active mode is shown to keep the toggle compact and clear. */}
        <Pressable onPress={toggleTheme} className="active:opacity-80">
          <Card className="flex-row items-center justify-between">
            <Text className="font-semibold" style={{ color: colors.textPrimary }}>
              {t('player.appearance') ?? 'Appearance'}
            </Text>
            <View className="h-9 w-9 items-center justify-center rounded-full" style={{ borderColor: colors.borderInactive, borderWidth: 1, backgroundColor: colors.surface }}>
              {isDark ? (
                <IconMoon size={18} color={colors.primary} />
              ) : (
                <IconSun size={18} color={colors.primary} />
              )}
            </View>
          </Card>
        </Pressable>

        <Pressable onPress={() => setShowInvite((v) => !v)} className="active:opacity-80">
          <Card className="flex-row justify-between items-center">
            <Text className="font-semibold" style={{ color: colors.textPrimary }}>{t('player.inviteFriends') ?? 'Invite friends'}</Text>
            <Text style={{ color: colors.primary }}>{showInvite ? '−' : '›'}</Text>
          </Card>
        </Pressable>

        {showInvite && (
          <Card className="gap-2">
            <Text className="font-semibold" style={{ color: colors.gold }}>{t('player.inviteFriends') ?? 'Invite friends'}</Text>
            <Text className="text-xs" style={{ color: colors.textSecondary }}>{t('player.shareYourLink') ?? 'Share your link'}</Text>
            {stats && (
              <View className="flex-row gap-2 mt-1">
                <View className="flex-1 rounded-xl px-3 py-2 items-center" style={{ backgroundColor: colors.surfaceAlt }}>
                  <Text className="text-lg font-bold" style={{ color: colors.textPrimary }}>{stats.totalRegistrations ?? 0}</Text>
                  <Text className="text-[10px]" style={{ color: colors.textSecondary }}>{t('player.friendsJoined') ?? 'Friends joined'}</Text>
                </View>
                <View className="flex-1 rounded-xl px-3 py-2 items-center" style={{ backgroundColor: colors.surfaceAlt }}>
                  <Text className="text-lg font-bold" style={{ color: colors.textPrimary }}>{stats.activeCodes ?? 0}</Text>
                  <Text className="text-[10px]" style={{ color: colors.textSecondary }}>{t('player.activeLinks') ?? 'Active links'}</Text>
                </View>
              </View>
            )}
            {link ? (
              <View className="rounded-xl border px-3 py-2" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}>
                <Text className="text-xs" style={{ color: colors.textSecondary }} numberOfLines={3}>
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

        {/* The nav bar holds a single icon, so every destination it used to
            point at is listed here instead. Each row pushes the screen rather
            than switching tabs, which keeps the bar itself meaning "profile". */}
        <Pressable onPress={() => router.push('/(player)/wallet')} className="active:opacity-80">
          <MenuRow
            icon={<IconWallet size={18} color={colors.textSecondary} />}
            label={t('mobile.navWallet') ?? 'Wallet'}
            detail={walletQuery?.data.balance?.toLocaleString()}
          />
        </Pressable>

        <Pressable onPress={() => router.push('/(player)/notifications')} className="active:opacity-80">
          <MenuRow
            icon={<IconBell size={18} color={colors.textSecondary} />}
            label={t('mobile.navAlerts') ?? 'Alerts'}
            badge={unread > 0 ? unread : undefined}
          />
        </Pressable>

        <Pressable onPress={() => router.push('/(player)/history')} className="active:opacity-80">
          <MenuRow
            icon={<IconHistory size={18} color={colors.textSecondary} />}
            label={t('player.gameHistory') ?? 'Game history'}
          />
        </Pressable>

        <Pressable onPress={() => setShowLanguage((v) => !v)} className="active:opacity-80">
          <Card className="flex-row justify-between items-center">
            <Text className="font-semibold" style={{ color: colors.textPrimary }}>{t('player.language') ?? 'Language'}</Text>
            <Text style={{ color: colors.primary }}>{showLanguage ? '−' : '›'}</Text>
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
                    className="flex-1 rounded-full border px-4 py-2 items-center"
                    style={{ borderColor: active ? colors.primary : colors.borderInactive, backgroundColor: active ? colors.primary + '20' : 'transparent' }}
                  >
                    <Text className={active ? 'font-semibold' : ''} style={{ color: active ? colors.textPrimary : colors.textSecondary }}>
                      {locale === 'en'
                        ? (t('player.english') ?? 'English')
                        : locale === 'am'
                          ? (t('player.amharic') ?? 'አማርኛ')
                          : (t('player.tigrinya') ?? 'ትግርኛ')}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </Card>
        )}

        <Button variant="ghost" onPress={handleLogout} style={{ marginTop: 12 }}>
          <Text style={{ color: colors.danger }}>{t('player.logOut') ?? 'Log out'}</Text>
        </Button>
      </ScrollView>
    </Screen>
  );
}

/** One destination row: icon, label, optional value or count, chevron. */
function MenuRow({
  icon,
  label,
  detail,
  badge,
}: {
  icon: React.ReactNode;
  label: string;
  detail?: string;
  badge?: number;
}) {
  const { colors } = useTheme();
  return (
    <Card className="flex-row items-center justify-between gap-3">
      <View className="h-8 w-8 items-center justify-center rounded-full" style={{ backgroundColor: colors.surfaceAlt }}>
        {icon}
      </View>
      <Text className="flex-1 font-semibold" style={{ color: colors.textPrimary }}>{label}</Text>
      {detail ? <Text className="text-sm" style={{ color: colors.textSecondary }}>{detail}</Text> : null}
      {badge ? (
        <View className="min-w-[20px] items-center justify-center rounded-full px-1.5 py-0.5" style={{ backgroundColor: colors.danger }}>
          <Text className="text-[10px] font-bold text-white">{badge}</Text>
        </View>
      ) : null}
      <Text style={{ color: colors.primary }}>›</Text>
    </Card>
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
  const { colors } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      accessibilityLabel={label}
      className="h-9 w-9 items-center justify-center rounded-full border"
      style={{ borderColor, opacity: active ? 1 : 0.7, backgroundColor: colors.surface }}
    >
      {children}
    </Pressable>
  );
}