import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { Alert, ScrollView, Text, View } from 'react-native';
import { agentsApi } from '@/api';
import { AppTextInput, Button, Card, EmptyState, Modal, Screen, SectionHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { AdminWarningResponse, AgentResponse, AgentStatsResponse } from '@/types';

const WARN_PRESETS = [
  'warnPresetSuspicious',
  'warnPresetSlow',
  'warnPresetComplaints',
  'warnPresetPolicy',
] as const;

export default function SuperAdminAgentsScreen() {
  const t = useTranslate();
  const qc = useQueryClient();
  const [warnAgent, setWarnAgent] = useState<AgentResponse | null>(null);
  const [suspendAgent, setSuspendAgent] = useState<AgentResponse | null>(null);
  const [statsAgent, setStatsAgent] = useState<AgentResponse | null>(null);
  const [inviteLink, setInviteLink] = useState<string | null>(null);
  const [inviteCopied, setInviteCopied] = useState(false);

  const { data } = useQuery({
    queryKey: ['super/agents'],
    queryFn: () => agentsApi.list(),
  });
  const agents: AgentResponse[] = data?.data ?? [];

  const invalidate = () => qc.invalidateQueries({ queryKey: ['super/agents'] });

  const inviteMutation = useMutation({
    mutationFn: () => agentsApi.invite(),
    onSuccess: (res) => {
      setInviteLink(res.data ?? null);
      setInviteCopied(false);
      invalidate();
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      agentsApi.updateStatus(id, { status }),
    onSuccess: () => {
      invalidate();
    },
    onError: (e) => Alert.alert((e as { userMessage?: string }).userMessage ?? 'Could not update agent'),
  });

  const { data: statsData, isFetching: statsLoading } = useQuery({
    queryKey: ['super/agents/stats', statsAgent?.adminUserId],
    queryFn: async () => {
      if (!statsAgent) return null;
      const [s, w] = await Promise.all([
        agentsApi.getStats(statsAgent.adminUserId),
        agentsApi.getWarnings(statsAgent.adminUserId),
      ]);
      return { stats: s.data, warnings: w.data };
    },
    enabled: statsAgent != null,
  });

  const pending = agents.filter((a) => !a.approved && a.active);
  const approved = agents.filter((a) => a.approved);

  const agentName = (a: AgentResponse) =>
    a.businessName ?? (a.username ? `@${a.username}` : (t('super.agentNumber', { id: String(a.adminUserId) }) ?? `Agent #${a.adminUserId}`));

  return (
    <Screen>
      <SectionHeader
        eyebrow={t('super.agEyebrow') ?? ''}
        title={t('super.agTitle') ?? 'Agent management'}
        description={t('super.agDesc') ?? ''}
        action={
          <Button
            variant="primary"
            disabled={inviteMutation.isPending}
            onPress={() => inviteMutation.mutate()}
            style={{ paddingVertical: 8, paddingHorizontal: 14 }}
          >
            {inviteMutation.isPending
              ? (t('super.generating') ?? 'Generating…')
              : (t('super.inviteAgent') ?? 'Invite agent')}
          </Button>
        }
      />

      <ScrollView contentContainerClassName="gap-4 pb-8">
        {pending.length > 0 && (
          <Card>
            <Text className="text-[11px] uppercase tracking-[0.24em] text-bp-textSecondary">
              {t('super.pendingApprovalHeader') ?? 'Pending approval'}
            </Text>
            <Text className="text-bp-textPrimary text-lg font-semibold mt-1">
              {t('super.awaitingReviewAgents', { count: pending.length }) ?? `${pending.length} agent(s) awaiting review`}
            </Text>
            <View className="gap-2 mt-4">
              {pending.map((a) => (
                <View
                  key={a.adminUserId}
                  className="rounded-[18px] border border-amber-500/30 bg-amber-500/5 px-4 py-3 gap-3"
                >
                  <View className="flex-row items-center justify-between">
                    <View className="flex-1 pr-2">
                      <Text className="text-bp-textPrimary font-semibold">{agentName(a)}</Text>
                      <Text className="text-bp-textSecondary text-xs">
                        {t('super.idLabel') ?? 'ID'} {a.adminUserId}
                      </Text>
                    </View>
                    <StatusPill status="PENDING" />
                  </View>
                  <View className="flex-row gap-2">
                    <Button
                      variant="success"
                      style={{ flex: 1, paddingVertical: 10 }}
                      disabled={statusMutation.isPending}
                      onPress={() => statusMutation.mutate({ id: a.adminUserId, status: 'APPROVE' })}
                    >
                      <Text className="text-white text-sm font-semibold">{t('admin.approve') ?? 'Approve'}</Text>
                    </Button>
                    <Button
                      variant="danger"
                      style={{ flex: 1, paddingVertical: 10 }}
                      disabled={statusMutation.isPending}
                      onPress={() => statusMutation.mutate({ id: a.adminUserId, status: 'REJECT' })}
                    >
                      <Text className="text-white text-sm font-semibold">{t('admin.reject') ?? 'Reject'}</Text>
                    </Button>
                  </View>
                </View>
              ))}
            </View>
          </Card>
        )}

        <Card>
          <Text className="text-[11px] uppercase tracking-[0.24em] text-bp-textSecondary">
            {t('super.registeredAgents') ?? 'Registered agents'}
          </Text>
          <Text className="text-bp-textPrimary text-lg font-semibold mt-1">
            {t('super.totalLabel', { count: agents.length }) ?? `${agents.length} total`}
          </Text>
          {approved.length === 0 && pending.length === 0 ? (
            <View className="mt-3">
              <EmptyState
                title={t('super.noAgentsFound') ?? 'No agents found'}
                description={t('super.noAgentsFoundDesc') ?? 'Invites will populate this list.'}
              />
            </View>
          ) : (
            <View className="gap-2 mt-4">
              {approved.map((a) => (
                <View
                  key={a.adminUserId}
                  className="rounded-[18px] border border-slate-800 bg-slate-900/60 px-4 py-3 gap-2"
                >
                  <View className="flex-row items-center justify-between">
                    <View className="flex-1 pr-2">
                      <Text className="text-bp-textPrimary font-semibold">{agentName(a)}</Text>
                      <Text className="text-bp-textSecondary text-xs">
                        {t('super.idLabel') ?? 'ID'} {a.adminUserId}
                      </Text>
                    </View>
                    <StatusPill status={a.active ? 'ACTIVE' : 'SUSPENDED'} />
                  </View>
                  <View className="flex-row gap-2 flex-wrap">
                    <Button variant="green" style={{ flex: 1 }} onPress={() => setStatsAgent(a)}>
                      <Text className="text-bp-successInk text-sm font-semibold">{t('super.stats') ?? 'Stats'}</Text>
                    </Button>
                    <Button variant="ghost" style={{ flex: 1 }} onPress={() => setWarnAgent(a)}>
                      <Text className="text-bp-textSecondary text-sm">{t('super.warn') ?? 'Warn'}</Text>
                    </Button>
                    {a.active ? (
                      <Button variant="danger" style={{ flex: 1 }} onPress={() => setSuspendAgent(a)}>
                        <Text className="text-white text-sm font-semibold">{t('super.suspend') ?? 'Suspend'}</Text>
                      </Button>
                    ) : (
                      <Button variant="success" style={{ flex: 1 }} onPress={() => statusMutation.mutate({ id: a.adminUserId, status: 'RESUME' })}>
                        <Text className="text-white text-sm font-semibold">{t('super.resume') ?? 'Resume'}</Text>
                      </Button>
                    )}
                  </View>
                </View>
              ))}
            </View>
          )}
        </Card>
      </ScrollView>

      {warnAgent && (
        <WarnDialog
          agent={warnAgent}
          onClose={() => setWarnAgent(null)}
          onWarn={(reason) => {
            statusMutation.mutate(
              { id: warnAgent.adminUserId, status: 'WARN' },
              { onSuccess: () => agentsApi.warn(warnAgent.adminUserId, reason).then(invalidate) }
            );
            setWarnAgent(null);
          }}
          t={t}
        />
      )}
      {suspendAgent && (
        <SuspendConfirmDialog
          agent={suspendAgent}
          onClose={() => setSuspendAgent(null)}
          onConfirm={() => {
            statusMutation.mutate({ id: suspendAgent.adminUserId, status: 'SUSPEND' });
            setSuspendAgent(null);
          }}
          t={t}
        />
      )}
      {statsAgent && (
        <StatsDialog
          agent={statsAgent}
          stats={statsData?.stats ?? null}
          warnings={statsData?.warnings ?? []}
          loading={statsLoading}
          onClose={() => setStatsAgent(null)}
          t={t}
        />
      )}
      {inviteLink && (
        <InviteLinkModal
          link={inviteLink}
          copied={inviteCopied}
          onCopy={() => {
            void Clipboard.setStringAsync(inviteLink);
            setInviteCopied(true);
            setTimeout(() => setInviteCopied(false), 2000);
          }}
          onClose={() => setInviteLink(null)}
          t={t}
        />
      )}
    </Screen>
  );
}

function InviteLinkModal({
  link,
  copied,
  onCopy,
  onClose,
  t,
}: {
  link: string;
  copied: boolean;
  onCopy: () => void;
  onClose: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="text-bp-textPrimary font-bold">
          {t('super.inviteAgentTitle') ?? 'New agent invite'}
        </Text>
        <Text className="text-bp-textSecondary text-sm">
          {t('super.inviteAgentDesc') ?? 'Share this link with your agent. It works once, opened in Telegram.'}
        </Text>
        <View className="rounded-xl bg-bp-surfaceAlt border border-bp-borderInactive px-3 py-2">
          <Text className="text-bp-textSecondary text-xs" numberOfLines={3}>
            {link}
          </Text>
        </View>
        <View className="flex-row gap-2">
          <Button variant="success" style={{ flex: 1 }} onPress={onCopy}>
            <Text className="text-white text-sm font-semibold">
              {copied ? (t('super.copied') ?? 'Copied!') : (t('super.copyLink') ?? 'Copy link')}
            </Text>
          </Button>
          <Button variant="neutral" style={{ flex: 1 }} onPress={onClose}>
            <Text className="text-bp-textPrimary text-sm">{t('super.close') ?? 'Close'}</Text>
          </Button>
        </View>
      </Card>
    </Modal>
  );
}

function WarnDialog({
  agent,
  onClose,
  onWarn,
  t,
}: {
  agent: AgentResponse;
  onClose: () => void;
  onWarn: (reason: string) => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const [preset, setPreset] = useState<string>('');
  const [custom, setCustom] = useState('');

  const confirm = () => {
    const reason = preset === '__custom__' ? custom.trim() : (t(`super.${preset}`) ?? preset);
    if (preset === '' || (preset === '__custom__' && !custom.trim())) return;
    onWarn(reason);
  };

  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="text-bp-textPrimary font-bold">{t('super.warnAgent') ?? 'Warn agent'}</Text>
        <Text className="text-bp-textSecondary text-sm">{agent.businessName ?? `@${agent.username ?? agent.adminUserId}`}</Text>
        <Text className="text-bp-textSecondary text-xs">{t('super.reasonLabel') ?? 'Reason'}</Text>
        {WARN_PRESETS.map((key) => {
          const selected = preset === key;
          return (
            <PressableRow
              key={key}
              selected={selected}
              onPress={() => setPreset(key)}
              label={t(`super.${key}`) ?? key}
              t={t}
            />
          );
        })}
        <PressableRow selected={preset === '__custom__'} onPress={() => setPreset('__custom__')} label={t('super.customReason') ?? 'Custom reason...'} t={t} />
        {preset === '__custom__' && (
          <AppTextInput value={custom} onChangeText={setCustom} placeholder={t('super.customReason') ?? 'Custom reason...'} />
        )}
        <View className="flex-row gap-2">
          <Button
            variant="danger"
            style={{ flex: 1 }}
            disabled={preset === '' || (preset === '__custom__' && !custom.trim())}
            onPress={confirm}
          >
            <Text className="text-white text-sm font-semibold">{t('super.warnBtn') ?? 'Warn'}</Text>
          </Button>
          <Button variant="neutral" style={{ flex: 1 }} onPress={onClose}>
            <Text className="text-bp-textPrimary text-sm">{t('super.cancel') ?? 'Cancel'}</Text>
          </Button>
        </View>
      </Card>
    </Modal>
  );
}

function SuspendConfirmDialog({
  agent,
  onClose,
  onConfirm,
  t,
}: {
  agent: AgentResponse;
  onClose: () => void;
  onConfirm: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="text-bp-textPrimary font-bold">
          {t('super.suspendTitle', { name: agent.businessName ?? `@${agent.username ?? agent.adminUserId}` }) ?? 'Suspend agent?'}
        </Text>
        <Text className="text-bp-textSecondary text-sm">
          {t('super.suspendDesc') ??
            "This will end all of the agent's active games and block their players from joining or playing until the agent is resumed."}
        </Text>
        <View className="flex-row gap-2">
          <Button variant="danger" style={{ flex: 1 }} onPress={onConfirm}>
            <Text className="text-white text-sm font-semibold">{t('super.suspendBtn') ?? 'Suspend'}</Text>
          </Button>
          <Button variant="neutral" style={{ flex: 1 }} onPress={onClose}>
            <Text className="text-bp-textPrimary text-sm">{t('super.cancel') ?? 'Cancel'}</Text>
          </Button>
        </View>
      </Card>
    </Modal>
  );
}

function StatsDialog({
  agent,
  stats,
  warnings,
  loading,
  onClose,
  t,
}: {
  agent: AgentResponse;
  stats: AgentStatsResponse | null;
  warnings: AdminWarningResponse[];
  loading: boolean;
  onClose: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const isFetching = loading && !stats;
  const rows: { label: string; value: string; cls?: string }[] = [
    { label: t('super.balance') ?? 'Balance', value: stats ? stats.balance.toLocaleString() : '—', cls: 'text-bp-goldInk' },
    { label: t('super.agentsWaiting') ?? 'Total Agents', value: stats ? String(stats.totalPlayers) : '—' },
    { label: t('super.barGames') ?? 'Games', value: stats ? String(stats.totalGames) : '—' },
    { label: t('super.completedStat') ?? 'Completed', value: stats ? String(stats.endedGames) : '—' },
    { label: t('super.transactions') ?? 'Transactions', value: stats ? String(stats.totalTransactions) : '—' },
    { label: t('super.commission') ?? 'Commission', value: stats ? stats.totalCommission.toLocaleString() : '—', cls: 'text-bp-accentInk' },
  ];

  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="text-bp-textPrimary font-bold">{t('super.agentStats') ?? 'Agent statistics'}</Text>
        <Text className="text-bp-textSecondary text-sm">
          {agent.businessName ?? `@${agent.username ?? agent.adminUserId}`}
        </Text>
        {isFetching ? (
          <Text className="text-bp-textSecondary text-sm">{t('super.status') ?? 'Loading...'}</Text>
        ) : (
          <View className="flex-row flex-wrap gap-2">
            {rows.map((r) => (
              <View key={r.label} className="bg-bp-surfaceAlt border border-bp-borderInactive rounded-xl px-3 py-2" style={{ width: '48%' }}>
                <Text className="text-bp-textInactive text-[10px] uppercase">{r.label}</Text>
                <Text className={`text-bp-textPrimary text-lg font-bold ${r.cls ?? ''}`}>{r.value}</Text>
              </View>
            ))}
          </View>
        )}
        <Text className="text-bp-textSecondary text-xs mt-1">{t('super.warningsLabel') ?? 'Warnings'}</Text>
        {warnings.length === 0 ? (
          <Text className="text-bp-textSecondary text-sm">{t('super.noWarningsRecorded') ?? 'No warnings recorded.'}</Text>
        ) : (
          <View className="gap-2">
            {warnings.slice(0, 5).map((w) => (
              <View key={w.id} className="border border-amber-400/30 bg-amber-400/5 rounded-xl px-3 py-2">
                <Text className="text-bp-textPrimary text-sm">{w.reason}</Text>
                <Text className="text-bp-textInactive text-xs mt-0.5">{new Date(w.createdAt).toLocaleDateString()}</Text>
              </View>
            ))}
          </View>
        )}
        <Button variant="neutral" onPress={onClose} style={{ marginTop: 8 }}>
          <Text className="text-bp-textPrimary text-sm">{t('super.close') ?? 'Close'}</Text>
        </Button>
      </Card>
    </Modal>
  );
}

function PressableRow({
  selected,
  onPress,
  label,
  t,
}: {
  selected: boolean;
  onPress: () => void;
  label: string;
  t: ReturnType<typeof useTranslate>;
}) {
  return (
    <Button
      variant={selected ? 'danger' : 'neutral'}
      onPress={onPress}
      style={{ paddingVertical: 10 }}
    >
      <Text className={selected ? 'text-white' : 'text-bp-textPrimary text-sm'}>{label}</Text>
    </Button>
  );
}