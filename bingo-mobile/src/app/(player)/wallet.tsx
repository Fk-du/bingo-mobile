import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Image, Pressable, ScrollView, Text, View } from 'react-native';
import {
  coinsApi,
  getApiErrorMessage,
  screenshotsApi,
  walletApi,
  withdrawalsApi,
} from '@/api';
import {
  AppTextInput,
  Button,
  Card,
  Screen,
  ScreenHeader,
  Subtitle,
  Title,
} from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useAuthStore } from '@/store/auth.store';
import { CoinRequestResponse, RequestStatus, WithdrawalResponse } from '@/types';
import { PaymentProof } from '@/components/PaymentProof';

const QUICK_AMOUNTS = [100, 250, 500, 1000];

type PickedProof = { uri: string; name: string; type: string };

const STATUS_STYLE: Record<RequestStatus, string> = {
  PENDING: 'bg-amber-400',
  APPROVED: 'bg-bp-accent',
  REJECTED: 'bg-bp-danger',
  CANCELLED: 'bg-bp-textInactive',
};

export default function WalletScreen() {
  const t = useTranslate();
  const qc = useQueryClient();
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  const { data: walletData } = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });
  const { data: coinRequestsData } = useQuery({
    queryKey: ['wallet', 'coin-requests'],
    queryFn: () => coinsApi.getRequests(),
  });
  const { data: withdrawalsData } = useQuery({
    queryKey: ['wallet', 'withdrawals'],
    queryFn: () => withdrawalsApi.list(),
  });

  const [buyAmount, setBuyAmount] = useState('');
  const [proof, setProof] = useState<PickedProof | null>(null);
  const [busy, setBusy] = useState(false);

  const balance = walletData?.data.balance ?? 0;
  const frozen = walletData?.data.frozenBalance ?? 0;
  const coinRequests = coinRequestsData?.data ?? [];
  const withdrawals = withdrawalsData?.data ?? [];

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
      void qc.invalidateQueries({ queryKey: ['wallet'] });
      void qc.invalidateQueries({ queryKey: ['wallet', 'coin-requests'] });
    } catch (e) {
      Alert.alert(t('common.error') ?? 'Error', getApiErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const requestWithdrawal = async () => {
    router.push('/(player)/withdraw');
  };

  type HistoryItem = { kind: 'topup'; id: number; amount: number; status: RequestStatus; rejectionReason: string | null; screenshotUrl: string | null; createdAt: string } | { kind: 'withdraw'; id: number; amount: number; status: RequestStatus; rejectionReason: string | null; screenshotUrl: null; createdAt: string };

  const history: HistoryItem[] = [
    ...coinRequests.map((r: CoinRequestResponse) => ({
      kind: 'topup' as const,
      id: r.id,
      amount: r.amount,
      status: r.status,
      rejectionReason: r.rejectionReason,
      screenshotUrl: r.screenshotUrl,
      createdAt: r.createdAt,
    })),
    ...withdrawals.map((w: WithdrawalResponse) => ({
      kind: 'withdraw' as const,
      id: w.id,
      amount: w.amount,
      status: w.status,
      rejectionReason: w.rejectionReason,
      screenshotUrl: null,
      createdAt: w.createdAt,
    })),
  ].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <Screen>
      <ScreenHeader title={t('mobile.navWallet') ?? 'Wallet'} />
      <ScrollView contentContainerClassName="gap-4 pb-8">
        <Card className="flex-row justify-between">
          <View>
            <Subtitle>{t('mobile.yourBalance') ?? 'Your Balance'}</Subtitle>
            <Title className="text-3xl">{balance.toLocaleString()}</Title>
            <Subtitle className="text-xs">
              {t('mobile.frozen') ?? 'Frozen'}: {frozen.toLocaleString()}
            </Subtitle>
          </View>
        </Card>

        <Card className="gap-3">
          <Text className="text-bp-textPrimary font-semibold">{t('player.buyCoins') ?? 'Buy Birr'}</Text>

          {user?.depositAccountInfo ? (
            <View className="gap-1 rounded-xl bg-bp-surfaceAlt border border-bp-borderInactive p-3">
              <Text className="text-xs font-semibold text-bp-secondaryInk">
                {t('player.sendDepositTo') ?? 'Send deposit to'}
              </Text>
              <Text className="text-sm text-bp-textPrimary">{user.depositAccountInfo}</Text>
              <Text className="text-xs text-bp-textSecondary">
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
              <Image source={{ uri: proof.uri }} className="h-14 w-14 rounded-lg bg-bp-surfaceAlt" resizeMode="cover" />
              <View className="flex-1">
                <Text className="text-xs text-bp-accentInk" numberOfLines={1}>
                  {t('player.attached', { filename: proof.name }) ?? proof.name}
                </Text>
              </View>
              <Pressable onPress={() => setProof(null)} disabled={busy}>
                <Text className="text-xs text-bp-dangerInk">{t('mobile.removeProof') ?? 'Remove'}</Text>
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

        <Card className="gap-2">
          <Text className="text-bp-textPrimary font-semibold">{t('mobile.withdraw') ?? 'Withdraw'}</Text>
          <Text className="text-bp-textSecondary text-sm">
            {t('player.withdrawFromWallet') ?? 'Request cash out of your balance.'}
          </Text>
          <Button onPress={() => void requestWithdrawal()} variant="primary">
            {t('mobile.requestWithdraw') ?? 'Withdraw'}
          </Button>
        </Card>

        <Card className="gap-2">
          <Text className="mb-1 text-bp-textPrimary font-semibold">
            {t('mobile.yourRequests') ?? 'Your Requests'}
          </Text>
          {history.length === 0 ? (
            <Text className="text-sm text-bp-textSecondary">{t('mobile.noRequests') ?? 'No requests yet'}</Text>
          ) : (
            history.map((item) => (
              <View
                key={`${item.kind}-${item.id}`}
                className="flex-row items-center justify-between bg-bp-surfaceAlt border border-bp-borderInactive rounded-xl px-3 py-2.5"
              >
                <View className="flex-1 pr-3">
                  <Text className="text-sm text-bp-textPrimary">
                    {item.kind === 'topup'
                      ? t('mobile.topUpRequest') ?? 'Top-up request'
                      : t('mobile.withdrawalRequest') ?? 'Withdrawal request'}
                  </Text>
                  <Text className="text-xs text-bp-textSecondary">
                    {new Date(item.createdAt).toLocaleDateString()}
                  </Text>
                  {item.status === 'REJECTED' && item.rejectionReason ? (
                    <Text className="text-xs text-bp-dangerInk" numberOfLines={1}>
                      {t('player.rejectedReason', { reason: item.rejectionReason }) ?? `Rejected: ${item.rejectionReason}`}
                    </Text>
                  ) : null}
                  {item.kind === 'topup' && item.screenshotUrl ? (
                    <View className="mt-1 w-10 h-10 rounded-lg overflow-hidden">
                      <PaymentProof url={item.screenshotUrl} size={40} />
                    </View>
                  ) : null}
                </View>
                <View className="items-end gap-1">
                  <Text className="text-sm font-bold text-bp-primary">+{item.amount.toLocaleString()}</Text>
                  <View className={`rounded-full px-2 py-0.5 ${STATUS_STYLE[item.status]}`}>
                    <Text className="text-[10px] font-semibold text-[#241a00]">
                      {t(`status.${item.status}`) ?? item.status}
                    </Text>
                  </View>
                </View>
              </View>
            ))
          )}
        </Card>

        <Text className="text-bp-textSecondary text-xs text-center">
          {t('mobile.walletHint') ?? 'Top-ups are reviewed by your agent before being credited.'}
        </Text>
      </ScrollView>
    </Screen>
  );
}