import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { withdrawalsApi } from '@/api';
import { AppTextInput, Button, Card, EmptyState, Screen, ScreenHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { RequestStatus, WithdrawalResponse } from '@/types';

type Tab = RequestStatus;

const REJECT_PRESETS = [
  'wdPresetNoScreenshot',
  'wdPresetUnclear',
  'wdPresetWrongScreenshot',
  'wdPresetNotRelated',
  'wdPresetInvalidDetails',
  'wdPresetDuplicate',
  'wdPresetInsufficient',
  'wdPresetFraud',
  'wdPresetUnavailable',
] as const;

function capCount(n: number): string {
  return n > 99 ? '99+' : String(n);
}

export default function AdminWithdrawalsScreen() {
  const t = useTranslate();
  const [tab, setTab] = useState<Tab>(RequestStatus.PENDING);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [preset, setPreset] = useState('');
  const [custom, setCustom] = useState('');
  const [actionMsg, setActionMsg] = useState<{ text: string; isError: boolean } | null>(null);

  const wdQuery = useQuery({ queryKey: ['admin/withdrawals'], queryFn: () => withdrawalsApi.list() });
  const withdrawals: WithdrawalResponse[] = wdQuery.data?.data ?? [];

  const pending = withdrawals.filter((w) => w.status === RequestStatus.PENDING);
  const paid = withdrawals.filter((w) => w.status === RequestStatus.APPROVED);
  const rejected = withdrawals.filter((w) => w.status === RequestStatus.REJECTED);

  const tabList = withdrawals.filter((w) => w.status === tab);

  useEffect(() => {
    if (!actionMsg) return;
    const id = setTimeout(() => setActionMsg(null), 4000);
    return () => clearTimeout(id);
  }, [actionMsg]);

  const pay = async (w: WithdrawalResponse) => {
    try {
      await withdrawalsApi.pay(w.id);
      setActionMsg({ text: t('admin.wdPaid') ?? 'Withdrawal marked as paid.', isError: false });
    } catch {
      setActionMsg({ text: t('admin.wdFailedPay') ?? 'Failed to mark as paid.', isError: true });
    } finally {
      await wdQuery.refetch();
    }
  };

  const reject = async (w: WithdrawalResponse) => {
    const reason =
      preset === '__custom__'
        ? custom.trim()
        : (t(`admin.${preset}`) ?? t('admin.wdPresetInvalidDetails') ?? 'Invalid payout details');
    if (preset === '' || (preset === '__custom__' && !custom.trim())) return;
    try {
      await withdrawalsApi.reject(w.id, reason);
      setActionMsg({ text: t('admin.wdRejected') ?? 'Withdrawal rejected.', isError: false });
      setRejectingId(null);
      setPreset('');
      setCustom('');
    } catch {
      setActionMsg({ text: t('admin.wdFailedReject') ?? 'Failed to reject withdrawal.', isError: true });
    } finally {
      await wdQuery.refetch();
    }
  };

  const chip = (label: string, active: boolean, onPress: () => void) => (
    <Button variant={active ? 'primary' : 'outline'} onPress={onPress} className="flex-1" style={{ paddingVertical: 8 }}>
      <Text className={active ? 'text-white' : 'text-bp-textSecondary text-xs'}>{label}</Text>
    </Button>
  );

  return (
    <Screen>
      <ScreenHeader title={t('admin.wdTitle') ?? 'Payout requests'} />

      <View className="flex-row gap-2 mb-3">
        {chip(t('admin.wdPendingTab', { count: capCount(pending.length) }) ?? `Pending (${capCount(pending.length)})`, tab === RequestStatus.PENDING, () => setTab(RequestStatus.PENDING))}
        {chip(t('admin.wdPaidTab', { count: paid.length }) ?? `Paid (${paid.length})`, tab === RequestStatus.APPROVED, () => setTab(RequestStatus.APPROVED))}
        {chip(t('admin.wdRejectedTab', { count: rejected.length }) ?? `Rejected (${rejected.length})`, tab === RequestStatus.REJECTED, () => setTab(RequestStatus.REJECTED))}
      </View>

      {actionMsg ? (
        <View className={`mb-3 rounded-xl border px-4 py-3 ${actionMsg.isError ? 'border-bp-danger40 bg-bp-danger10' : 'border-bp-accent40 bg-bp-accent10'}`}>
          <Text className={actionMsg.isError ? 'text-bp-dangerInk text-sm' : 'text-bp-accentInk text-sm'}>
            {actionMsg.isError ? '✕ ' : '✓ '}
            {actionMsg.text}
          </Text>
        </View>
      ) : null}

      <FlatList
        data={tabList}
        extraData={getClientLocale()}
        keyExtractor={(w) => String(w.id)}
        refreshControl={
          <RefreshControl
            refreshing={wdQuery.isFetching}
            onRefresh={() => wdQuery.refetch()}
            tintColor="#6B5BFF"
          />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <EmptyState
            title={t('admin.wdNoWithdrawals') ?? 'No withdrawals here'}
            description={t('admin.wdNoWithdrawalsView', { status: tab.toLowerCase() }) ?? `No ${tab.toLowerCase()} withdrawals in this view.`}
          />
        }
        renderItem={({ item }) => {
          const isRejecting = rejectingId === item.id;
          return (
            <Card className="gap-2">
              <View className="flex-row items-center justify-between">
                <Text className="text-bp-textPrimary font-semibold">
                  {t('admin.wdCoins', { amount: String(item.amount) }) ?? `${item.amount} birr`}
                </Text>
                <StatusPill status={item.status} />
              </View>
              {item.payoutDetails ? (
                <Text className="text-bp-textSecondary text-sm">{item.payoutDetails}</Text>
              ) : null}
              {item.rejectionReason ? (
                <Text className="text-bp-dangerInk text-xs">
                  {t('admin.wdReason', { reason: item.rejectionReason }) ?? `Reason: ${item.rejectionReason}`}
                </Text>
              ) : null}

              {item.status === RequestStatus.PENDING && !isRejecting ? (
                <View className="flex-row gap-2">
                  <Button variant="primary" style={{ flex: 1 }} onPress={() => void pay(item)}>
                    {t('admin.wdPay') ?? 'Pay'}
                  </Button>
                  <Button
                    variant="danger"
                    style={{ flex: 1 }}
                    onPress={() => {
                      setRejectingId(item.id);
                      setPreset('');
                      setCustom('');
                    }}
                  >
                    {t('admin.wdRejectBtn') ?? 'Reject'}
                  </Button>
                </View>
              ) : null}

              {isRejecting ? (
                <View className="gap-2">
                  <Text className="text-bp-textSecondary text-xs">{t('admin.wdSelectReason') ?? 'Select a reason'}</Text>
                  {REJECT_PRESETS.map((r) => {
                    const selected = preset === r;
                    return (
                      <PresetRow
                        key={r}
                        selected={selected}
                        onPress={() => setPreset(r)}
                        label={t(`admin.${r}`) ?? r}
                      />
                    );
                  })}
                  <PresetRow
                    selected={preset === '__custom__'}
                    onPress={() => setPreset('__custom__')}
                    label={t('admin.wdCustomReason') ?? 'Custom reason...'}
                  />
                  {preset === '__custom__' && (
                    <AppTextInput value={custom} onChangeText={setCustom} placeholder={t('admin.wdCustomPlaceholder') ?? 'Enter custom reason'} />
                  )}
                  <View className="flex-row gap-2">
                    <Button
                      variant="danger"
                      style={{ flex: 1 }}
                      disabled={preset === '' || (preset === '__custom__' && !custom.trim())}
                      onPress={() => void reject(item)}
                    >
                      {t('admin.wdConfirm') ?? 'Confirm'}
                    </Button>
                    <Button
                      variant="outline"
                      style={{ flex: 1 }}
                      onPress={() => {
                        setRejectingId(null);
                        setPreset('');
                        setCustom('');
                      }}
                    >
                      {t('admin.wdCancel') ?? 'Cancel'}
                    </Button>
                  </View>
                </View>
              ) : null}
            </Card>
          );
        }}
      />
    </Screen>
  );
}

function PresetRow({ selected, onPress, label }: { selected: boolean; onPress: () => void; label: string }) {
  return (
    <Button
      variant={selected ? 'danger' : 'outline'}
      onPress={onPress}
      style={{ paddingVertical: 10 }}
    >
      <Text className={selected ? 'text-white' : 'text-bp-textPrimary text-sm'}>{label}</Text>
    </Button>
  );
}