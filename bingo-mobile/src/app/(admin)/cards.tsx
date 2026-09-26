import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { cardsApi } from '@/api';
import { AppTextInput, Button, Card, EmptyState, Metric, Screen, ScreenHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { CardRequestResponse } from '@/types';

const QUICK_CHIPS = [25, 50, 100];

export default function AdminCardsScreen() {
  const t = useTranslate();
  const [quantity, setQuantity] = useState('50');
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const poolQuery = useQuery({
    queryKey: ['admin/cards/available'],
    queryFn: () => cardsApi.getAvailable(1, 100),
    staleTime: 5_000,
  });
  const requestsQuery = useQuery({
    queryKey: ['admin/cards/requests'],
    queryFn: () => cardsApi.getCardRequests(),
    refetchInterval: 15_000,
  });

  const pool = poolQuery.data?.data;
  const requests: CardRequestResponse[] = requestsQuery.data?.data ?? [];
  const pending = requests.filter((r) => r.status === 'PENDING');
  const cardsRequested = requests.reduce((sum, r) => sum + r.quantity, 0);

  useEffect(() => {
    if (!success) return;
    const id = setTimeout(() => setSuccess(null), 5000);
    return () => clearTimeout(id);
  }, [success]);

  const submit = async () => {
    const value = Number(quantity);
    if (!value || value < 1 || value > 500) return;
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      await cardsApi.createCardRequest(value);
      setSuccess(t('admin.cardsRequestSubmitted') ?? 'Card request submitted! The super admin will add them shortly.');
      await requestsQuery.refetch();
    } catch (e) {
      setError((e as { userMessage?: string }).userMessage ?? 'Could not submit request');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('admin.cardsTitle') ?? 'Card pool'} />

      <View className="flex-row gap-3 mb-4">
        <Metric
          label={t('admin.availableCards') ?? 'Available Cards'}
          value={pool ? pool.total.toLocaleString() : '—'}
          tone="success"
        />
        <Metric label={t('admin.pendingRequests') ?? 'Pending Requests'} value={pending.length} tone="warning" />
        <Metric
          label={t('admin.cardsRequested') ?? 'Cards Requested'}
          value={requests ? cardsRequested.toLocaleString() : '—'}
          tone="primary"
        />
      </View>

      <FlatList
        data={requests}
        extraData={getClientLocale()}
        keyExtractor={(r) => String(r.id)}
        refreshControl={
          <RefreshControl
            refreshing={requestsQuery.isFetching}
            onRefresh={() => requestsQuery.refetch()}
            tintColor="#6B5BFF"
          />
        }
        contentContainerClassName="gap-3 pb-8"
        ListHeaderComponent={
          <>
            <Card className="gap-3 mb-2">
              <Text className="text-bp-textSecondary text-[11px] uppercase tracking-wide">
                {t('admin.needMore') ?? 'Need more'}
              </Text>
              <Text className="text-bp-textPrimary font-bold">{t('admin.requestCards') ?? 'Request cards'}</Text>
              <Text className="text-bp-textSecondary text-sm">
                {t('admin.requestCardsDesc') ?? 'Cards are shared across all your games.'}
              </Text>
              <View className="flex-row flex-wrap gap-2">
                {QUICK_CHIPS.map((q) => (
                  <Button
                    key={q}
                    variant={quantity === String(q) ? 'primary' : 'outline'}
                    onPress={() => setQuantity(String(q))}
                    style={{ paddingVertical: 8, paddingHorizontal: 14 }}
                  >
                    +{q}
                  </Button>
                ))}
              </View>
              <AppTextInput
                value={quantity}
                onChangeText={setQuantity}
                keyboardType="numeric"
                placeholder={t('admin.quantity') ?? 'Quantity'}
              />
              {error ? <Text className="text-bp-dangerInk text-sm">✕ {error}</Text> : null}
              {success ? <Text className="text-bp-accentInk text-sm">✓ {success}</Text> : null}
              <Button disabled={busy} onPress={() => void submit()}>
                {busy ? (t('admin.requesting') ?? 'Requesting…') : (t('admin.requestCardsBtn') ?? 'Request cards')}
              </Button>
              <Text className="text-bp-textInactive text-[10px] text-center">
                {t('admin.requestCardsCost') ?? 'Each card costs a small fee from your agent balance.'}
              </Text>
            </Card>
            <Text className="text-bp-textSecondary text-[11px] uppercase tracking-wide mt-2 mb-1">
              {t('admin.history') ?? 'History'}
            </Text>
          </>
        }
        ListEmptyComponent={
          <EmptyState
            title={t('admin.noCardRequests') ?? 'No card requests'}
            description={t('admin.noCardRequestsDesc') ?? 'New requests will appear here.'}
          />
        }
        renderItem={({ item }) => (
          <Card className="gap-1">
            <View className="flex-row items-center justify-between">
              <Text className="text-bp-textPrimary font-semibold">
                {item.quantity} {t('admin.availableCards') ?? 'card'}
                {item.quantity !== 1 ? 's' : ''}
              </Text>
              <StatusPill status={item.status} />
            </View>
            <Text className="text-bp-textSecondary text-xs">{new Date(item.createdAt).toLocaleString()}</Text>
            {item.status === 'REJECTED' && item.rejectionReason ? (
              <Text className="text-bp-dangerInk text-xs">
                {t('admin.wdReason', { reason: item.rejectionReason }) ?? `Reason: ${item.rejectionReason}`}
              </Text>
            ) : null}
          </Card>
        )}
      />
    </Screen>
  );
}