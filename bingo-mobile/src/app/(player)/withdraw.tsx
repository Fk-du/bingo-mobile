import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { configApi, getApiErrorMessage, walletApi, withdrawalsApi } from '@/api';
import { AppTextInput, Button, Card, Screen, ScreenHeader, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { RequestStatus, WithdrawalResponse } from '@/types';

const QUICK_AMOUNTS = [10, 20, 50, 100];
const DEFAULT_MIN_WITHDRAWAL = 10;

export default function PlayerWithdrawScreen() {
  const t = useTranslate();
  const qc = useQueryClient();
  const { colors } = useTheme();
  const [amount, setAmount] = useState('');
  const [details, setDetails] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState<WithdrawalResponse | null>(null);
  const [busy, setBusy] = useState(false);

  const { data: walletData } = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });
  const { data: withdrawalsData } = useQuery({
    queryKey: ['wallet', 'withdrawals'],
    queryFn: () => withdrawalsApi.list(),
  });
  const { data: configData } = useQuery({ queryKey: ['player/config'], queryFn: () => configApi.get() });

  const balance = walletData?.data.balance ?? 0;
  const withdrawals: WithdrawalResponse[] = withdrawalsData?.data ?? [];
  const minWithdrawal = Number(configData?.data?.minWithdrawal) || DEFAULT_MIN_WITHDRAWAL;

  useEffect(() => {
    if (!submitted) return;
    const id = setTimeout(() => setSubmitted(null), 8000);
    return () => clearTimeout(id);
  }, [submitted]);

  const send = async () => {
    const value = Number(amount);
    if (!value || !details.trim()) return;
    setError(null);
    if (value < minWithdrawal) {
      setError(t('player.withdrawMin', { amount: String(minWithdrawal) }) ?? `Minimum withdrawal is ${minWithdrawal} birr`);
      return;
    }
    if (value > balance) {
      setError(t('player.withdrawMax', { balance: String(balance) }) ?? `You can't withdraw more than your balance (${balance} birr)`);
      return;
    }
    setBusy(true);
    try {
      const res = await withdrawalsApi.create({ amount: value, payoutDetails: details.trim() });
      setSubmitted(res.data);
      setAmount('');
      setDetails('');
      void qc.invalidateQueries({ queryKey: ['wallet'] });
      void qc.invalidateQueries({ queryKey: ['wallet', 'withdrawals'] });
    } catch (e) {
      setError(getApiErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('mobile.withdraw') ?? 'Withdraw'} />

      <ScrollView contentContainerClassName="gap-4 pb-8">
        <Card className="gap-4">
          <View>
            <Subtitle>{t('mobile.yourBalance') ?? 'Your Balance'}</Subtitle>
            <Title className="text-3xl">{balance.toLocaleString()}</Title>
            <Subtitle className="text-xs">
              {t('player.minWithdrawalInfo', { amount: String(minWithdrawal) }) ??
                `Minimum withdrawal: ${minWithdrawal} birr`}
            </Subtitle>
          </View>

          <View className="flex-row flex-wrap gap-2">
            {QUICK_AMOUNTS.map((q) => (
              <Button
                key={q}
                variant="outline"
                disabled={busy}
                onPress={() => setAmount(String(q))}
                style={{ paddingVertical: 8, paddingHorizontal: 14 }}
              >
                {q.toLocaleString()}
              </Button>
            ))}
          </View>

          <AppTextInput
            value={amount}
            onChangeText={setAmount}
            placeholder={t('mobile.amount') ?? 'Amount (birr)'}
            keyboardType="numeric"
          />
          <AppTextInput
            value={details}
            onChangeText={setDetails}
            placeholder={t('mobile.payoutDetails') ?? 'Bank account or Telebirr number'}
          />
          <Text className="text-xs" style={{ color: colors.textSecondary }}>
            {t('player.withdrawHint') ?? 'e.g. CBE 1000987654321 or Telebirr +251912345678'}
          </Text>

          {error ? <Text className="text-sm" style={{ color: colors.danger }}>✕ {error}</Text> : null}

          <Button disabled={busy || !amount || !details.trim()} onPress={() => void send()}>
            {busy
              ? (t('player.submitting') ?? 'Submitting…')
              : (t('player.requestWithdrawal') ?? 'Request withdrawal')}
          </Button>
        </Card>

        {submitted ? (
          <Card style={{ borderColor: '#36E4B440', backgroundColor: '#36E4B510' }}>
            <Text className="font-semibold" style={{ color: colors.accent }}>
              {t('player.payoutSuccessTitle') ?? 'Thank you!'}
            </Text>
            <Text className="text-sm mt-1" style={{ color: colors.textPrimary }}>
              {t('player.payoutSuccessMessage', {
                amount: String(submitted.amount),
                details: submitted.payoutDetails ?? details,
              }) ??
                `Your withdrawal of ${submitted.amount} birr (${submitted.payoutDetails ?? 'payout details'}) has been submitted for review. Keep an eye on your notifications.`}
            </Text>
          </Card>
        ) : null}

        <Card className="gap-2">
          <Text className="mb-1 font-semibold" style={{ color: colors.textPrimary }}>
            {t('player.withdrawalHistory') ?? 'Withdrawal history'}
          </Text>
          {withdrawals.length === 0 ? (
            <Text className="text-sm" style={{ color: colors.textSecondary }}>
              {t('player.noWithdrawals') ?? 'No withdrawals yet'}
            </Text>
          ) : (
            withdrawals.map((w) => (
              <View
                key={w.id}
                className="flex-row items-center justify-between rounded-xl px-3 py-2.5"
                style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}
              >
                <View className="flex-1 pr-3">
                  <Text className="text-sm font-semibold" style={{ color: colors.textPrimary }}>{w.amount.toLocaleString()}</Text>
                  <Text className="text-xs" style={{ color: colors.textSecondary }}>
                    {new Date(w.createdAt).toLocaleDateString()}
                    {w.payoutDetails ? ` · ${w.payoutDetails}` : ''}
                  </Text>
                  {w.status === RequestStatus.REJECTED && w.rejectionReason ? (
                    <Text className="text-xs" style={{ color: colors.danger }} numberOfLines={1}>
                      {t('player.rejectedReason', { reason: w.rejectionReason }) ?? `Rejected: ${w.rejectionReason}`}
                    </Text>
                  ) : null}
                </View>
                <View className="rounded-full px-2 py-0.5" style={{ backgroundColor: colors.surfaceAlt }}>
                  <Text className="text-[10px] font-semibold" style={{ color: colors.textSecondary }}>
                    {t(`status.${w.status}`) ?? w.status}
                  </Text>
                </View>
              </View>
            ))
          )}
        </Card>

        <Text className="text-xs text-center" style={{ color: colors.textSecondary }}>
          {t('player.withdrawFooterHint') ?? 'Your withdrawal stays pending until the agent approves and pays it out.'}
        </Text>
      </ScrollView>
    </Screen>
  );
}