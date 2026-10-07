import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { coinsApi, playersApi } from '@/api';
import { PaymentProof } from '@/components/PaymentProof';
import { AppTextInput, Button, Card, EmptyState, Metric, Modal, Screen, ScreenHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { CoinRequestResponse, RequestStatus } from '@/types';

type Tab = Exclude<RequestStatus, RequestStatus.CANCELLED>;

const COMMON_REASONS = [
  'reasonProofUnclear',
  'reasonAmountMismatch',
  'reasonNoProof',
  'reasonDuplicate',
  'reasonRecordsMismatch',
  'reasonNotReceived',
] as const;

export default function AdminCoinsScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const [tab, setTab] = useState<Tab>(RequestStatus.PENDING);
  const [reviewing, setReviewing] = useState<CoinRequestResponse | null>(null);

  const coinsQuery = useQuery({ queryKey: ['admin/coins'], queryFn: () => coinsApi.getRequests() });
  const playersQuery = useQuery({ queryKey: ['admin/players'], queryFn: () => playersApi.list() });

  const coins: CoinRequestResponse[] = coinsQuery.data?.data ?? [];
  const players = playersQuery.data?.data ?? [];

  const pending = coins.filter((c) => c.status === RequestStatus.PENDING);
  const approved = coins.filter((c) => c.status === RequestStatus.APPROVED);
  const rejected = coins.filter((c) => c.status === RequestStatus.REJECTED);
  const approvedTotal = approved.reduce((sum, c) => sum + c.amount, 0);

  const tabList = coins.filter((c) => c.status === tab);

  const playerLabel = (userId: number) =>
    players.some((p) => p.userId === userId)
      ? (t('admin.playerNumber', { id: String(userId) }) ?? `Player #${userId}`)
      : (t('admin.userNumber', { id: String(userId) }) ?? `User #${userId}`);

  const chip = (label: string, active: boolean, onPress: () => void) => (
    <Button variant={active ? 'primary' : 'outline'} onPress={onPress} className="flex-1" style={{ paddingVertical: 8 }}>
      <Text style={{ color: active ? '#FFFFFF' : colors.textSecondary }} className="text-xs">{label}</Text>
    </Button>
  );

  return (
    <Screen>
      <ScreenHeader title={t('admin.topUpTitle') ?? 'Top-up approvals'} />

      <View className="flex-row gap-3 mb-3">
        <Metric label={t('admin.pendingMetric') ?? 'Pending'} value={pending.length} tone="warning" />
        <Metric
          label={t('admin.approvedTotalMetric') ?? 'Approved Total'}
          value={approvedTotal.toLocaleString()}
          tone="success"
        />
      </View>

      <View className="flex-row gap-2 mb-3">
        {chip(t('admin.pendingTab', { count: pending.length }) ?? `Pending (${pending.length})`, tab === RequestStatus.PENDING, () => setTab(RequestStatus.PENDING))}
        {chip(t('admin.approvedTab', { count: approved.length }) ?? `Approved (${approved.length})`, tab === RequestStatus.APPROVED, () => setTab(RequestStatus.APPROVED))}
        {chip(t('admin.rejectedTab', { count: rejected.length }) ?? `Rejected (${rejected.length})`, tab === RequestStatus.REJECTED, () => setTab(RequestStatus.REJECTED))}
      </View>

      <FlatList
        data={tabList}
        extraData={getClientLocale()}
        keyExtractor={(c) => String(c.id)}
        refreshControl={
          <RefreshControl
            refreshing={coinsQuery.isFetching}
            onRefresh={() => coinsQuery.refetch()}
            tintColor="#6B5BFF"
          />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <EmptyState
            title={t('admin.noRequestsHere') ?? 'No requests here'}
            description={t('admin.noRequestsView', { status: tab.toLowerCase() }) ?? `No ${tab.toLowerCase()} requests in this view.`}
          />
        }
        renderItem={({ item }) => (
          <Card className="gap-1">
            <View className="flex-row items-center gap-3">
              <PaymentProof url={item.screenshotUrl} size={48} />
              <View className="flex-1">
                <Text className="text-sm font-semibold" style={{ color: colors.textPrimary }}>
                  {t('admin.wdCoins', { amount: String(item.amount) }) ?? `${item.amount} birr`}
                </Text>
                <Text className="text-xs" style={{ color: colors.textSecondary }}>
                  {playerLabel(item.userId)} · {new Date(item.createdAt).toLocaleDateString()}
                </Text>
              </View>
              {item.status === RequestStatus.PENDING ? (
                <Button variant="primary" onPress={() => setReviewing(item)} style={{ paddingVertical: 6, paddingHorizontal: 12 }}>
                  <Text className="text-white text-xs font-semibold">{t('admin.review') ?? 'Review'}</Text>
                </Button>
              ) : (
                <StatusPill status={item.status} />
              )}
            </View>
            {item.rejectionReason ? (
              <Text className="text-xs" style={{ color: colors.danger }}>
                {t('player.reason', { reason: item.rejectionReason }) ?? `Reason: ${item.rejectionReason}`}
              </Text>
            ) : null}
          </Card>
        )}
      />

      {reviewing && (
        <ReviewDialog
          request={reviewing}
          playerLabel={playerLabel(reviewing.userId)}
          onClose={() => setReviewing(null)}
          onDone={() => coinsQuery.refetch()}
          t={t}
        />
      )}
    </Screen>
  );
}

function ReviewDialog({
  request,
  playerLabel,
  onClose,
  onDone,
  t,
}: {
  request: CoinRequestResponse;
  playerLabel: string;
  onClose: () => void;
  onDone: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const { colors } = useTheme();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const act = async (action: 'APPROVE' | 'REJECT') => {
    setBusy(true);
    try {
      await coinsApi.handleRequest(request.id, {
        action,
        ...(action === 'REJECT' && reason ? { reason } : {}),
      });
      await onDone();
      onClose();
    } catch {
      // mutation failures surface on refetch; dialog stays open
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="font-bold" style={{ color: colors.textPrimary }}>{t('admin.reviewRequest') ?? 'Review Request'}</Text>
        <Text className="text-sm" style={{ color: colors.textSecondary }}>
          {t('admin.requestedCoins', { player: playerLabel, amount: String(request.amount) }) ?? `${playerLabel} requested ${request.amount} birr`}
        </Text>

        <Text className="text-[11px] uppercase tracking-wide" style={{ color: colors.textInactive }}>{t('admin.paymentProof') ?? 'Payment Proof'}</Text>
        {request.screenshotUrl ? (
          <View className="items-center">
            <PaymentProof url={request.screenshotUrl} size={160} />
            <Text className="text-xs mt-1" style={{ color: colors.textSecondary }}>{t('admin.tapToEnlarge') ?? 'Tap the image to enlarge'}</Text>
          </View>
        ) : (
          <Text className="text-sm" style={{ color: colors.textSecondary }}>{t('admin.noScreenshot') ?? 'No screenshot attached.'}</Text>
        )}

        <Text className="text-[11px] uppercase tracking-wide" style={{ color: colors.textInactive }}>{t('admin.rejectionReason') ?? 'Rejection Reason'}</Text>
        {COMMON_REASONS.map((key) => {
          const selected = reason === (t(`admin.${key}`) ?? key);
          return (
            <Pressable
              key={key}
              onPress={() => setReason(t(`admin.${key}`) ?? key)}
              className="rounded-xl border px-3 py-2"
              style={{ borderColor: selected ? colors.danger : colors.borderInactive, backgroundColor: selected ? colors.danger + '15' : 'transparent' }}
            >
              <Text className={selected ? 'text-sm font-semibold' : 'text-sm'} style={{ color: selected ? colors.danger : colors.textPrimary }}>
                {t(`admin.${key}`) ?? key}
              </Text>
            </Pressable>
          );
        })}
        <Text className="text-xs" style={{ color: colors.textSecondary }}>{t('admin.customReason') ?? 'Custom…'}</Text>
        <AppTextInput value={reason} onChangeText={setReason} placeholder={t('admin.reasonOnlyReject') ?? 'Only used when rejecting'} />

        <View className="flex-row gap-2">
          <Button variant="outline" style={{ flex: 1 }} onPress={onClose} disabled={busy}>
            {t('admin.back') ?? 'Back'}
          </Button>
          <Button variant="danger" style={{ flex: 1 }} disabled={busy} onPress={() => void act('REJECT')}>
            {t('admin.reject') ?? 'Reject'}
          </Button>
          <Button variant="primary" style={{ flex: 1 }} disabled={busy} onPress={() => void act('APPROVE')}>
            {t('admin.approve') ?? 'Approve'}
          </Button>
        </View>
      </Card>
    </Modal>
  );
}