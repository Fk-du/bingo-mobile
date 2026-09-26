import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { cardsApi } from '@/api';
import { AppTextInput, Button, EmptyState, Metric, Screen, SectionHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { CardRequestResponse } from '@/types';

export default function SuperAdminCardsScreen() {
  const t = useTranslate();
  const qc = useQueryClient();
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [processError, setProcessError] = useState<string | null>(null);

  const { data, isFetching, refetch } = useQuery({
    queryKey: ['super/cards/requests'],
    queryFn: () => cardsApi.getCardRequests(),
    refetchInterval: 15_000,
  });
  const requests: CardRequestResponse[] = data?.data ?? [];

  const processMutation = useMutation({
    mutationFn: ({ id, action, reason: r }: { id: number; action: string; reason?: string }) =>
      cardsApi.processCardRequest(id, { action, reason: r || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['super/cards/requests'] });
      qc.invalidateQueries({ queryKey: ['super/cards'] });
    },
    onError: (e) => {
      setProcessError((e as { userMessage?: string }).userMessage ?? 'Could not process request');
    },
  });

  const pending = requests.filter((r) => r.status === 'PENDING');
  const history = requests.filter((r) => r.status !== 'PENDING');
  const cardsRequested = requests.reduce((sum, r) => sum + r.quantity, 0);

  const approve = (id: number) => {
    setProcessError(null);
    processMutation.mutate({ id, action: 'APPROVE' });
  };

  const reject = (id: number) => {
    setProcessError(null);
    processMutation.mutate(
      { id, action: 'REJECT', reason: reason.trim() || undefined },
      {
        onSuccess: () => {
          setRejectId(null);
          setReason('');
        },
      }
    );
  };

  return (
    <Screen>
      <SectionHeader
        eyebrow={t('super.scEyebrow') ?? ''}
        title={t('super.scTitle') ?? 'Card pool requests'}
        description={t('super.scDesc') ?? ''}
      />

      <View className="flex-row gap-3 mb-4">
        <Metric
          label={t('admin.pendingRequests') ?? 'Pending Requests'}
          value={pending.length}
          tone="warning"
        />
        <Metric
          label={t('admin.cardsRequested') ?? 'Cards Requested'}
          value={data ? cardsRequested.toLocaleString() : '—'}
          tone="primary"
        />
      </View>

      {processError ? (
        <View className="mb-4 rounded-xl border border-bp-danger40 bg-bp-danger15 px-4 py-3">
          <Text className="text-red-500 text-sm">✕ {processError}</Text>
        </View>
      ) : null}

      <FlatList
        data={pending}
        extraData={getClientLocale()}
        keyExtractor={(r) => String(r.id)}
        refreshControl={
          <RefreshControl
            refreshing={isFetching}
            onRefresh={() => refetch()}
            tintColor="#6B5BFF"
          />
        }
        contentContainerClassName="gap-3 pb-8"
        ListHeaderComponent={
          <Text className="text-[11px] uppercase tracking-[0.24em] text-bp-textSecondary mt-2 mb-1">
            {t('super.pendingWord') ?? 'Pending'}
          </Text>
        }
        ListEmptyComponent={
          <EmptyState
            title={t('super.noPendingRequests') ?? 'No pending requests'}
            description={t('super.noPendingRequestsDesc') ?? 'Agent card requests will appear here.'}
          />
        }
        renderItem={({ item }) => (
          <View className="rounded-[18px] border border-bp-borderInactive bg-bp-surface60 p-4 gap-3">
            <View className="flex-1">
              <Text className="text-bp-textPrimary font-semibold">
                {t('super.agentId', { id: String(item.adminUserId) }) ?? `Agent ID ${item.adminUserId}`}
              </Text>
              <Text className="text-bp-textSecondary text-xs mt-0.5">
                {t('super.requestsNewCards', { count: String(item.quantity) }) ?? `Requests ${item.quantity} new cards`} ·{' '}
                {new Date(item.createdAt).toLocaleString()}
              </Text>
            </View>
            <View className="flex-row gap-2 flex-wrap">
              <Button
                variant="success"
                disabled={processMutation.isPending}
                onPress={() => approve(item.id)}
              >
                <Text className="text-white text-sm font-semibold">
                  {processMutation.isPending ? '…' : (t('super.approveBtn') ?? 'Approve')}
                </Text>
              </Button>
              {rejectId === item.id ? (
                <>
                  <AppTextInput
                    value={reason}
                    onChangeText={setReason}
                    placeholder={t('super.reasonOptional') ?? 'Reason (optional)'}
                    className="flex-[2]"
                  />
                  <Button variant="danger" onPress={() => reject(item.id)}>
                    <Text className="text-white text-sm">{t('admin.confirm') ?? 'Confirm'}</Text>
                  </Button>
                  <Button variant="neutral" onPress={() => { setRejectId(null); setReason(''); }}>
                    <Text className="text-bp-textPrimary text-sm">{t('admin.cancel') ?? 'Cancel'}</Text>
                  </Button>
                </>
              ) : (
                <Button
                  variant="neutral"
                  onPress={() => { setRejectId(item.id); setReason(''); setProcessError(null); }}
                >
                  <Text className="text-bp-textPrimary text-sm">{t('super.rejectBtn') ?? 'Reject'}</Text>
                </Button>
              )}
            </View>
          </View>
        )}
        ListFooterComponent={
          history.length > 0 ? (
            <View>
              <Text className="text-[11px] uppercase tracking-[0.24em] text-bp-textSecondary mt-5 mb-2">
                {t('admin.history') ?? 'History'}
              </Text>
              {history.map((item) => (
                <View
                  key={item.id}
                  className="flex-row items-center justify-between gap-3 rounded-[18px] border border-bp-borderInactive bg-bp-surface60 px-4 py-3 mb-2"
                >
                  <View className="flex-1 pr-2">
                    <Text className="text-bp-textPrimary text-sm font-semibold">
                      {t('super.agentId', { id: String(item.adminUserId) }) ?? `Agent ID ${item.adminUserId}`} ·{' '}
                      {t('super.requestsNewCards', { count: String(item.quantity) }) ?? `Requests ${item.quantity} new cards`}
                    </Text>
                    <Text className="text-bp-textSecondary text-xs mt-0.5">
                      {new Date(item.createdAt).toLocaleDateString()}
                      {item.approvedAt
                        ? ` · ${t('super.approvedOn') ?? 'approved'} ${new Date(item.approvedAt).toLocaleDateString()}`
                        : ''}
                    </Text>
                    {item.status === 'REJECTED' && item.rejectionReason ? (
                      <Text className="text-bp-dangerInk text-xs mt-0.5">
                        {t('admin.wdReason', { reason: item.rejectionReason }) ?? `Reason: ${item.rejectionReason}`}
                      </Text>
                    ) : null}
                  </View>
                  <StatusPill status={item.status} />
                </View>
              ))}
            </View>
          ) : null
        }
      />
    </Screen>
  );
}