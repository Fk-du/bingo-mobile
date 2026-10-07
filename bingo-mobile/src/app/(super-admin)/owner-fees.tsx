import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { agentsApi } from '@/api';
import { PaymentProof } from '@/components/PaymentProof';
import { AppTextInput, Button, Card, EmptyState, Metric, Modal, Screen, SectionHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { AdminOwnerFeeSummaryResponse, OwnerFeeSettlementResponse } from '@/types';

export default function SuperAdminOwnerFeesScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const qc = useQueryClient();
  const [managing, setManaging] = useState<AdminOwnerFeeSummaryResponse | null>(null);

  const summariesQuery = useQuery({
    queryKey: ['super/fee-summary'],
    queryFn: () => agentsApi.getAllFeeSummary(),
  });
  const settlementsQuery = useQuery({
    queryKey: ['super/fee-settlements'],
    queryFn: () => agentsApi.getFeeSettlements(),
  });

  const summaries: AdminOwnerFeeSummaryResponse[] = summariesQuery.data?.data ?? [];
  const settlements: OwnerFeeSettlementResponse[] = settlementsQuery.data?.data ?? [];

  const totalOwed = summaries.reduce((sum, s) => sum + s.owed, 0);
  const totalSettled = summaries.reduce((sum, s) => sum + s.settled, 0);
  const pending = settlements.filter((s) => s.status === 'PENDING');
  const totalPending = pending.reduce((sum, s) => sum + s.amount, 0);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['super/fee-summary'] });
    qc.invalidateQueries({ queryKey: ['super/fee-settlements'] });
  };

  const handleMutation = useMutation({
    mutationFn: ({ id, action, reason }: { id: number; action: string; reason?: string }) =>
      agentsApi.handleFeeSettlement(id, { action, reason: reason || undefined }),
    onSuccess: invalidate,
  });

  const agentName = (s: AdminOwnerFeeSummaryResponse) =>
    s.businessName ?? s.username ?? (t('super.agentNumber', { id: String(s.adminUserId) }) ?? `Agent #${s.adminUserId}`);

  const manageSettlements = managing
    ? settlements
        .filter((s) => s.adminUserId === managing.adminUserId)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    : [];

  return (
    <Screen>
      <SectionHeader
        eyebrow={t('super.sfEyebrow') ?? ''}
        title={t('super.sfTitle') ?? 'Agent fee ledger'}
        description={t('super.sfDesc') ?? ''}
      />

      <ScrollView contentContainerClassName="gap-4 pb-8">
        <View className="flex-row gap-3">
          <Metric
            label={t('super.cashOwedUnpaid') ?? 'Cash Owed'}
            value={summariesQuery.isLoading ? '...' : totalOwed.toLocaleString()}
            tone={totalOwed > 0 ? 'gold' : 'success'}
            note={t('super.totalOwedDesc') ?? 'Total unpaid owner fees'}
          />
          <Metric
            label={t('super.pendingReviewMetric') ?? 'Pending Review'}
            value={summariesQuery.isLoading ? '...' : `${pending.length} · ${totalPending.toLocaleString()}`}
            tone="warning"
            note={t('super.pendingReviewDesc') ?? 'Payments awaiting confirmation'}
          />
          <Metric
            label={t('super.settledCashPaid') ?? 'Settled Cash'}
            value={summariesQuery.isLoading ? '...' : totalSettled.toLocaleString()}
            tone="success"
            note={t('super.settledDesc') ?? 'Confirmed cash payments'}
          />
        </View>

        {summaries.length === 0 ? (
          <EmptyState
            title={t('super.noAgents') ?? 'No agents'}
            description={t('super.noAgentsDesc') ?? 'Agents will appear here once they are onboarded.'}
          />
        ) : (
          summaries.map((s) => {
            const perPending = pending.filter((p) => p.adminUserId === s.adminUserId).length;
            return (
              <Card key={s.adminUserId} className="gap-2">
                <View className="flex-row items-center justify-between">
                  <Text className="font-semibold flex-1 pr-2" style={{ color: colors.textPrimary }}>{agentName(s)}</Text>
                  <Button
                    variant="neutral"
                    onPress={() => setManaging(s)}
                    style={{ paddingVertical: 6, paddingHorizontal: 12 }}
                  >
                    <Text className="text-xs font-semibold" style={{ color: colors.textPrimary }}>
                      {t('super.manage') ?? 'Manage'}
                    </Text>
                  </Button>
                </View>
                <View className="flex-row flex-wrap gap-2">
                  <Stat label={t('super.accruedWord') ?? 'Accrued'} value={s.accrued.toLocaleString()} />
                  <Stat label={t('super.paidWord') ?? 'Paid'} value={s.settled.toLocaleString()} color={colors.accent} />
                  <Stat
                    label={t('super.owedWord') ?? 'Owed'}
                    value={s.owed.toLocaleString()}
                    color={s.owed > 0 ? colors.danger : colors.accent}
                    bold={s.owed > 0}
                  />
                  <Stat
                    label={t('super.lastPaid') ?? 'Last Paid'}
                    value={s.lastSettledAt ? new Date(s.lastSettledAt).toLocaleDateString() : '—'}
                  />
                  <View className="flex-1 items-start">
                    <Text className="text-[10px] uppercase" style={{ color: colors.textInactive }}>
                      {t('super.pendingWord') ?? 'Pending'}
                    </Text>
                    {perPending > 0 ? (
                      <Text className="text-sm font-semibold" style={{ color: colors.gold }}>
                        {t('super.toReview', { count: String(perPending) }) ?? `${perPending} to review`}
                      </Text>
                    ) : (
                      <Text className="text-sm" style={{ color: colors.textSecondary }}>—</Text>
                    )}
                  </View>
                </View>
              </Card>
            );
          })
        )}
      </ScrollView>

      {managing && (
        <ManageDialog
          agent={managing}
          settlements={manageSettlements}
          pending={handleMutation.isPending}
          onApprove={(id) => handleMutation.mutate({ id, action: 'APPROVE' })}
          onReject={(id, reason) => handleMutation.mutate({ id, action: 'REJECT', reason })}
          onClose={() => setManaging(null)}
          t={t}
        />
      )}
    </Screen>
  );
}

function Stat({ label, value, color, bold }: { label: string; value: string; color?: string; bold?: boolean }) {
  const { colors } = useTheme();
  return (
    <View className="flex-1 items-start">
      <Text className="text-[10px] uppercase" style={{ color: colors.textInactive }}>{label}</Text>
      <Text className={bold ? 'text-sm font-semibold' : 'text-sm'} style={{ color: color ?? colors.textPrimary }}>{value}</Text>
    </View>
  );
}

function ManageDialog({
  agent,
  settlements,
  pending,
  onApprove,
  onReject,
  onClose,
  t,
}: {
  agent: AdminOwnerFeeSummaryResponse;
  settlements: OwnerFeeSettlementResponse[];
  pending: boolean;
  onApprove: (id: number) => void;
  onReject: (id: number, reason?: string) => void;
  onClose: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const { colors } = useTheme();
  const [showReasonFor, setShowReasonFor] = useState<number | null>(null);
  const [reason, setReason] = useState('');

  const agentLabel =
    agent.businessName ?? agent.username ?? (t('super.agentNumber', { id: String(agent.adminUserId) }) ?? `Agent #${agent.adminUserId}`);

  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <View className="flex-row items-center justify-between">
          <Text className="font-bold" style={{ color: colors.textPrimary }}>{agentLabel}</Text>
          <PressableClose onPress={onClose} label={t('super.close') ?? 'Close'} />
        </View>
        <View className="flex-row gap-2 flex-wrap">
          <Text className="text-sm" style={{ color: colors.textSecondary }}>
            {t('super.accruedWord') ?? 'Accrued'} {agent.accrued.toLocaleString()}
          </Text>
          <Text className="text-sm" style={{ color: colors.textSecondary }}>
            · {t('super.paidWord') ?? 'Paid'} <Text style={{ color: colors.accent }}>{agent.settled.toLocaleString()}</Text>
          </Text>
          <Text className="text-sm" style={{ color: colors.textSecondary }}>
            · {t('super.owedWord') ?? 'Owed'}{' '}
            <Text className={agent.owed > 0 ? 'font-bold' : ''} style={{ color: agent.owed > 0 ? colors.danger : colors.accent }}>
              {agent.owed.toLocaleString()}
            </Text>
          </Text>
        </View>

        {settlements.length === 0 ? (
          <EmptyState title={t('super.noSettlementsAgent') ?? 'No cash payments from this agent yet.'} />
        ) : (
          <View className="gap-2">
            {settlements.map((s) => (
              <View key={s.id} className="rounded-xl px-3 py-2.5 gap-1.5" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}>
                <View className="flex-row items-center gap-3">
                  <PaymentProof url={s.screenshotUrl} size={56} />
                  <View className="flex-1">
                    <Text className="text-sm font-semibold" style={{ color: colors.textPrimary }}>
                      {t('admin.cashPaid', { amount: String(s.amount) }) ?? `${s.amount} cash paid`}
                    </Text>
                    <Text className="text-xs" style={{ color: colors.textSecondary }}>{new Date(s.createdAt).toLocaleDateString()}</Text>
                    {s.rejectionReason ? (
                      <Text className="text-xs" style={{ color: colors.danger }}>
                        {t('admin.wdReason', { reason: s.rejectionReason }) ?? `Reason: ${s.rejectionReason}`}
                      </Text>
                    ) : null}
                  </View>
                  <StatusPill status={s.status} />
                </View>
                {s.status === 'PENDING' ? (
                  showReasonFor === s.id ? (
                    <View className="gap-2">
                      <AppTextInput value={reason} onChangeText={setReason} placeholder={t('super.reasonLabel') ?? 'Reason'} />
                      <View className="flex-row gap-2">
                        <Button
                          variant="success"
                          style={{ flex: 1 }}
                          disabled={pending}
                          onPress={() => {
                            onApprove(s.id);
                            onClose();
                          }}
                        >
                          <Text className="text-white text-sm font-semibold">{t('admin.approve') ?? 'Approve'}</Text>
                        </Button>
                        <Button
                          variant="danger"
                          style={{ flex: 1 }}
                          disabled={pending}
                          onPress={() => {
                            onReject(s.id, reason.trim() || undefined);
                            onClose();
                          }}
                        >
                          <Text className="text-white text-sm font-semibold">{t('admin.reject') ?? 'Reject'}</Text>
                        </Button>
                      </View>
                    </View>
                  ) : (
                    <Button
                      variant="success"
                      onPress={() => {
                        setShowReasonFor(s.id);
                        setReason('');
                      }}
                      style={{ paddingVertical: 6 }}
                    >
                      <Text className="text-white text-xs font-semibold">{t('admin.review') ?? 'Review'}</Text>
                    </Button>
                  )
                ) : null}
              </View>
            ))}
          </View>
        )}

        <Button variant="neutral" onPress={onClose} style={{ marginTop: 4 }}>
          <Text className="text-sm" style={{ color: colors.textPrimary }}>{t('super.done') ?? 'Done'}</Text>
        </Button>
      </Card>
    </Modal>
  );
}

function PressableClose({ onPress, label }: { onPress: () => void; label: string }) {
  const { colors } = useTheme();
  return (
    <Button variant="neutral" onPress={onPress} style={{ paddingVertical: 6, paddingHorizontal: 12 }}>
      <Text className="text-xs" style={{ color: colors.textPrimary }}>{label}</Text>
    </Button>
  );
}