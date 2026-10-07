import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Text, View } from 'react-native';
import { coinsApi, walletApi, withdrawalsApi } from '@/api';
import { Button, Card, Screen, ScreenBackButton, ScreenHeader, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { CoinRequestResponse, RequestStatus, WithdrawalResponse } from '@/types';
import { PaymentProof } from '@/components/PaymentProof';

const STATUS_STYLE: Record<RequestStatus, string> = {
  PENDING: '#f59e0b',
  APPROVED: '#6B5BFF',
  REJECTED: '#FF5C6C',
  CANCELLED: '#9ca3af',
};

export default function WalletScreen() {
  const t = useTranslate();
  const router = useRouter();
  const { colors } = useTheme();

  const { data: walletData } = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });
  const { data: coinRequestsData } = useQuery({
    queryKey: ['wallet', 'coin-requests'],
    queryFn: () => coinsApi.getRequests(),
  });
  const { data: withdrawalsData } = useQuery({
    queryKey: ['wallet', 'withdrawals'],
    queryFn: () => withdrawalsApi.list(),
  });

  const balance = walletData?.data.balance ?? 0;
  const frozen = walletData?.data.frozenBalance ?? 0;
  const coinRequests = coinRequestsData?.data ?? [];
  const withdrawals = withdrawalsData?.data ?? [];

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
      <ScreenHeader title={t('mobile.navWallet') ?? 'Wallet'} left={<ScreenBackButton />} />

      <View className="flex-1 justify-between">
        <View className="gap-4 pb-4">
          <Card className="flex-row justify-between">
            <View>
              <Subtitle>{t('mobile.yourBalance') ?? 'Your Balance'}</Subtitle>
              <Title className="text-3xl">{balance.toLocaleString()}</Title>
              <Subtitle className="text-xs">
                {t('mobile.frozen') ?? 'Frozen'}: {frozen.toLocaleString()}
              </Subtitle>
            </View>
          </Card>

          <Card className="gap-2">
            <Text className="mb-1 font-semibold" style={{ color: colors.textPrimary }}>
              {t('mobile.yourRequests') ?? 'Your Requests'}
            </Text>
            {history.length === 0 ? (
              <Text className="text-sm" style={{ color: colors.textSecondary }}>{t('mobile.noRequests') ?? 'No requests yet'}</Text>
            ) : (
              history.map((item) => (
                <View
                  key={`${item.kind}-${item.id}`}
                  className="flex-row items-center justify-between rounded-xl px-3 py-2.5"
                  style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}
                >
                  <View className="flex-1 pr-3">
                    <Text className="text-sm" style={{ color: colors.textPrimary }}>
                      {item.kind === 'topup'
                        ? t('mobile.topUpRequest') ?? 'Top-up request'
                      : t('mobile.withdrawalRequest') ?? 'Withdrawal request'}
                    </Text>
                    <Text className="text-xs" style={{ color: colors.textSecondary }}>
                      {new Date(item.createdAt).toLocaleDateString()}
                    </Text>
                    {item.status === 'REJECTED' && item.rejectionReason ? (
                      <Text className="text-xs" style={{ color: colors.danger }} numberOfLines={1}>
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
                    <Text className="text-sm font-bold" style={{ color: item.kind === 'topup' ? colors.primary : colors.danger }}>
                      {item.kind === 'topup' ? `+${item.amount.toLocaleString()}` : `-${item.amount.toLocaleString()}`}
                    </Text>
                    <View className="rounded-full px-2 py-0.5" style={{ backgroundColor: colors.surfaceAlt }}>
                      <Text className="text-[10px] font-semibold" style={{ color: STATUS_STYLE[item.status] }}>
                        {t(`status.${item.status}`) ?? item.status}
                      </Text>
                    </View>
                  </View>
                </View>
              ))
            )}
          </Card>
        </View>

        <View className="pb-6 pt-2 gap-2">
          <Button onPress={() => router.push('/(player)/deposit')} variant="gold">
            {t('mobile.buyCoins') ?? 'Deposit'}
          </Button>
          <Button onPress={() => router.push('/(player)/withdraw')} variant="primary">
            {t('mobile.withdraw') ?? 'Withdraw'}
          </Button>
        </View>
      </View>
    </Screen>
  );
}
