import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Image, Pressable, ScrollView, Text, View } from 'react-native';
import { authApi, coinsApi, getApiErrorMessage, inviteApi, notificationsApi, screenshotsApi, walletApi, withdrawalsApi } from '@/api';
import { AppTextInput, Button, Card, Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import {
  IconBell,
  IconHistory,
  IconMoon,
  IconSun,
} from '@/components/ui/icons';
import { getClientLocale, setClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { useAuthStore } from '@/store/auth.store';
import { CoinRequestResponse, RequestStatus, WithdrawalResponse } from '@/types';
import { PaymentProof } from '@/components/PaymentProof';

const LOCALES = ['en', 'am', 'ti'] as const;

const QUICK_AMOUNTS = [100, 250, 500, 1000];

const STATUS_COLOR: Record<RequestStatus, string> = {
  PENDING: '#f59e0b',
  APPROVED: '#6B5BFF',
  REJECTED: '#FF5C6C',
  CANCELLED: '#9ca3af',
};

type PickedProof = { uri: string; name: string; type: string };

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
  const [showTransactions, setShowTransactions] = useState(false);
  const [showDeposit, setShowDeposit] = useState(false);
  const [buyAmount, setBuyAmount] = useState('');
  const [proof, setProof] = useState<PickedProof | null>(null);
  const [busy, setBusy] = useState(false);

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

  const { data: coinRequestsData } = useQuery({
    queryKey: ['wallet', 'coin-requests'],
    queryFn: () => coinsApi.getRequests(),
  });
  const { data: withdrawalsData } = useQuery({
    queryKey: ['wallet', 'withdrawals'],
    queryFn: () => withdrawalsApi.list(),
  });

  const coinRequests = coinRequestsData?.data ?? [];
  const withdrawals = withdrawalsData?.data ?? [];

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

  const qc = useQueryClient();

  const pickProof = async () => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert(t('mobile.proofUploadFailed') ?? 'Upload failed', t('mobile.photosDenied') ?? '');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.7,
    });
    if (result.canceled) return;
    const asset = result.assets[0];
    if (!asset) return;
    setProof({
      uri: asset.uri,
      name: asset.fileName ?? 'payment-proof.jpg',
      type: asset.mimeType ?? 'image/jpeg',
    });
  };

  const requestTopUp = async () => {
    const amount = Number(buyAmount);
    if (!amount || amount <= 0) return;
    if (!proof) {
      Alert.alert(t('mobile.proofUploadFailed') ?? 'Upload failed', t('player.noScreenshotError') ?? '');
      return;
    }
    setBusy(true);
    try {
      const uploaded = await screenshotsApi.upload(proof);
      await coinsApi.createRequest({ amount, screenshotUrl: uploaded.data });
      Alert.alert(t('mobile.topUpSent') ?? 'Payment request sent', t('mobile.afterSending') ?? '');
      setBuyAmount('');
      setProof(null);
      setShowDeposit(false);
      void qc.invalidateQueries({ queryKey: ['wallet'] });
    } catch (e) {
      Alert.alert(t('common.error') ?? 'Error', getApiErrorMessage(e));
    } finally {
      setBusy(false);
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

        {/* The wallet area shows the balance and the two action buttons directly
            so the player never has to open a screen just to find Deposit or
            Withdraw. */}
        <View className="gap-4">
          <Card className="w-full items-center justify-center gap-4 p-5">
            <View className="flex-row items-center justify-center gap-6">
              <View className="items-center">
                <Text className="text-[10px] font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
                  {t('mobile.yourBalance') ?? 'Balance'}
                </Text>
                <Text className="text-xl font-bold" style={{ color: colors.textPrimary }}>
                  {walletQuery?.data?.balance?.toLocaleString() ?? '—'}
                </Text>
              </View>
              <View className="items-center">
                <Text className="text-[10px] font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
                  {t('mobile.frozen') ?? 'Frozen'}
                </Text>
                <Text className="text-xl font-bold" style={{ color: colors.textPrimary }}>
                  {walletQuery?.data?.frozenBalance?.toLocaleString() ?? '—'}
                </Text>
              </View>
            </View>

            <View className="flex-row gap-3 w-full pt-2">
              <Button
                onPress={() => setShowDeposit((v) => !v)}
                variant="gold"
                style={{ flex: 1 }}
              >
                {t('mobile.buyCoins') ?? 'Deposit'}
              </Button>
              <Button
                onPress={() => router.push('/(player)/withdraw')}
                variant="primary"
                style={{ flex: 1 }}
              >
                {t('mobile.withdraw') ?? 'Withdraw'}
              </Button>
            </View>
          </Card>
        </View>

        {showDeposit && (
          <Card className="gap-3">
            <Text className="font-semibold" style={{ color: colors.textPrimary }}>
              {t('player.buyCoins') ?? 'Buy Birr'}
            </Text>

            {user?.depositAccountInfo ? (
              <View className="gap-1 rounded-xl border p-3" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}>
                <Text className="text-xs font-semibold" style={{ color: colors.secondary }}>
                  {t('player.sendDepositTo') ?? 'Send deposit to'}
                </Text>
                <Text className="text-sm" style={{ color: colors.textPrimary }}>{user.depositAccountInfo}</Text>
                <Text className="text-xs" style={{ color: colors.textSecondary }}>
                  {t('player.afterSending') ?? 'After sending, upload your payment screenshot below.'}
                </Text>
              </View>
            ) : null}

            <View className="flex-row flex-wrap gap-2">
              {QUICK_AMOUNTS.map((q) => (
                <Button
                  key={q}
                  variant="outline"
                  disabled={busy}
                  onPress={() => setBuyAmount(String(q))}
                  style={{ paddingVertical: 8, paddingHorizontal: 14 }}
                >
                  {q.toLocaleString()}
                </Button>
              ))}
            </View>

            <AppTextInput
              value={buyAmount}
              onChangeText={setBuyAmount}
              placeholder={t('mobile.amount') ?? 'Amount (birr)'}
              keyboardType="numeric"
            />

            {proof ? (
              <View className="flex-row items-center gap-3">
                <Image source={{ uri: proof.uri }} className="h-14 w-14 rounded-lg" resizeMode="cover" style={{ backgroundColor: colors.surfaceAlt }} />
                <View className="flex-1">
                  <Text className="text-xs" style={{ color: colors.accent }} numberOfLines={1}>
                    {t('player.attached', { filename: proof.name }) ?? proof.name}
                  </Text>
                </View>
                <Pressable onPress={() => setProof(null)} disabled={busy}>
                  <Text className="text-xs" style={{ color: colors.danger }}>{t('mobile.removeProof') ?? 'Remove'}</Text>
                </Pressable>
              </View>
            ) : (
              <Button variant="outline" onPress={pickProof} disabled={busy}>
                {t('mobile.pickScreenshot') ?? 'Attach payment screenshot'}
              </Button>
            )}

            <Button onPress={requestTopUp} disabled={busy}>
              {t('mobile.requestPayment') ?? 'Request payment'}
            </Button>
          </Card>
        )}

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

        <Pressable onPress={() => setShowTransactions((v) => !v)} className="active:opacity-80">
          <Card className="flex-row justify-between items-center">
            <Text className="font-semibold" style={{ color: colors.textPrimary }}>
              {t('player.transactionHistory') ?? t('player.recentTransactions') ?? 'Transaction History'}
            </Text>
            <Text style={{ color: colors.primary }}>{showTransactions ? '−' : '›'}</Text>
          </Card>
        </Pressable>

        {showTransactions && (
          <TransactionHistory
            coinRequests={coinRequests}
            withdrawals={withdrawals}
            colors={colors}
            t={t}
          />
        )}

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

type TxItem = {
  id: number;
  kind: 'topup' | 'withdraw';
  amount: number;
  status: RequestStatus;
  rejectionReason: string | null;
  screenshotUrl: string | null;
  createdAt: string;
};

function TransactionHistory({
  coinRequests,
  withdrawals,
  colors,
  t,
}: {
  coinRequests: CoinRequestResponse[];
  withdrawals: WithdrawalResponse[];
  colors: ReturnType<typeof useTheme>['colors'];
  t: ReturnType<typeof useTranslate>;
}) {
  const transactions: TxItem[] = [
    ...coinRequests.map((r) => ({
      id: r.id,
      kind: 'topup' as const,
      amount: r.amount,
      status: r.status,
      rejectionReason: r.rejectionReason,
      screenshotUrl: r.screenshotUrl,
      createdAt: r.createdAt,
    })),
    ...withdrawals.map((w) => ({
      id: w.id,
      kind: 'withdraw' as const,
      amount: w.amount,
      status: w.status,
      rejectionReason: w.rejectionReason,
      screenshotUrl: null,
      createdAt: w.createdAt,
    })),
  ].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <Card className="gap-3">
      <Text className="text-xs font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
        {t('player.recentTransactions') ?? 'Recent Transactions'}
      </Text>
      {transactions.length === 0 ? (
        <Text className="text-sm" style={{ color: colors.textSecondary }}>
          {t('player.noTransactions') ?? 'No transactions yet.'}
        </Text>
      ) : (
        transactions.map((tx) => (
          <View
            key={`${tx.kind}-${tx.id}`}
            className="flex-row items-center justify-between rounded-xl px-3 py-2.5"
          style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}
        >
          <View className="flex-1 pr-3">
            <Text className="text-sm" style={{ color: colors.textPrimary }}>
              {tx.kind === 'topup'
                ? (t('mobile.topUpRequest') ?? 'Top-up request')
                : (t('mobile.withdrawalRequest') ?? 'Withdrawal request')}
            </Text>
            <Text className="text-xs" style={{ color: colors.textSecondary }}>
              {new Date(tx.createdAt).toLocaleDateString()}
            </Text>
            {tx.status === 'REJECTED' && tx.rejectionReason ? (
              <Text className="text-xs" style={{ color: colors.danger }} numberOfLines={1}>
                {t('player.rejectedReason', { reason: tx.rejectionReason }) ?? `Rejected: ${tx.rejectionReason}`}
              </Text>
            ) : null}
            {tx.kind === 'topup' && tx.screenshotUrl ? (
              <View className="mt-1 w-10 h-10 rounded-lg overflow-hidden">
                <PaymentProof url={tx.screenshotUrl} size={40} />
              </View>
            ) : null}
          </View>
          <View className="items-end gap-1">
            <Text className="text-sm font-bold" style={{ color: tx.kind === 'topup' ? colors.primary : colors.danger }}>
              {tx.kind === 'topup' ? `+${tx.amount.toLocaleString()}` : `-${tx.amount.toLocaleString()}`}
            </Text>
            <View className="rounded-full px-2 py-0.5" style={{ backgroundColor: colors.surfaceAlt }}>
              <Text className="text-[10px] font-semibold" style={{ color: STATUS_COLOR[tx.status] }}>
                {t(`status.${tx.status}`) ?? tx.status}
              </Text>
            </View>
          </View>
        </View>
      )))}
    </Card>
  );
}