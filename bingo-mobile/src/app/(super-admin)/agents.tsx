import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { Alert, ScrollView, Text, View } from 'react-native';
import { agentsApi } from '@/api';
import { AppTextInput, Button, Card, EmptyState, Modal, Screen, SectionHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { AdminWarningResponse, AgentResponse, AgentStatsResponse } from '@/types';

const WARN_PRESETS = [
  'warnPresetSuspicious',
  'warnPresetSlow',
  'warnPresetComplaints',
  'warnPresetPolicy',
] as const;

const GRID_ITEM = { flexBasis: '47%', flexGrow: 1 } as const;

export default function SuperAdminAgentsScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const qc = useQueryClient();
  const [warnAgent, setWarnAgent] = useState<AgentResponse | null>(null);
  const [suspendAgent, setSuspendAgent] = useState<AgentResponse | null>(null);
  const [deleteAgent, setDeleteAgent] = useState<AgentResponse | null>(null);
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

  // Unlike a warn or a suspend, a delete cannot be undone, so the server refuses
  // it while a game is open or money is pending and reports what it removed. The
  // refusal arrives as a userMessage and is shown as-is: it is the instruction
  // (suspend first, settle first) that makes the next attempt succeed.
  const deleteMutation = useMutation({
    mutationFn: (id: number) => agentsApi.remove(id),
    onSuccess: (res) => {
      setDeleteAgent(null);
      setStatsAgent(null);
      invalidate();
      Alert.alert(t('super.deleteDoneTitle') ?? 'Agent deleted', res.message);
    },
    onError: (e) => Alert.alert((e as { userMessage?: string }).userMessage ?? (t('super.deleteFailed') ?? 'Could not delete agent')),
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
            <Text className="text-[11px] uppercase tracking-[0.24em]" style={{ color: colors.textSecondary }}>
              {t('super.pendingApprovalHeader') ?? 'Pending approval'}
            </Text>
            <Text className="text-lg font-semibold mt-1" style={{ color: colors.textPrimary }}>
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
                      <Text className="font-semibold" style={{ color: colors.textPrimary }}>{agentName(a)}</Text>
                      <Text className="text-xs" style={{ color: colors.textSecondary }}>
                        {t('super.idLabel', { id: String(a.adminUserId) }) ?? `ID ${a.adminUserId}`}
                      </Text>
                    </View>
                    <StatusPill status="PENDING" />
                  </View>
                  <View className="flex-row gap-2">
                    <Button
                      compact
                      variant="success"
                      style={GRID_ITEM}
                      disabled={statusMutation.isPending}
                      onPress={() => statusMutation.mutate({ id: a.adminUserId, status: 'APPROVE' })}
                    >
                      <Text className="text-white text-xs font-semibold">{t('admin.approve') ?? 'Approve'}</Text>
                    </Button>
                    <Button
                      compact
                      variant="danger"
                      style={GRID_ITEM}
                      disabled={statusMutation.isPending}
                      onPress={() => statusMutation.mutate({ id: a.adminUserId, status: 'REJECT' })}
                    >
                      <Text className="text-white text-xs font-semibold">{t('admin.reject') ?? 'Reject'}</Text>
                    </Button>
                  </View>
                </View>
              ))}
            </View>
          </Card>
        )}

        <Card>
          <Text className="text-[11px] uppercase tracking-[0.24em]" style={{ color: colors.textSecondary }}>
            {t('super.registeredAgents') ?? 'Registered agents'}
          </Text>
          <Text className="text-lg font-semibold mt-1" style={{ color: colors.textPrimary }}>
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
                      <Text className="font-semibold" style={{ color: colors.textPrimary }}>{agentName(a)}</Text>
                      <Text className="text-xs" style={{ color: colors.textSecondary }}>
                        {t('super.idLabel', { id: String(a.adminUserId) }) ?? `ID ${a.adminUserId}`}
                      </Text>
                    </View>
                    <StatusPill status={a.active ? 'ACTIVE' : 'SUSPENDED'} />
                  </View>
                  <View className="flex-row gap-2 flex-wrap">
                    <Button compact variant="green" style={GRID_ITEM} onPress={() => setStatsAgent(a)}>
                      <Text className="text-xs font-semibold" style={{ color: colors.success }}>{t('super.stats') ?? 'Stats'}</Text>
                    </Button>
                    <Button compact variant="ghost" style={GRID_ITEM} onPress={() => setWarnAgent(a)}>
                      <Text className="text-xs" style={{ color: colors.textSecondary }}>{t('super.warn') ?? 'Warn'}</Text>
                    </Button>
                    {a.active ? (
                      <Button compact variant="danger" style={GRID_ITEM} onPress={() => setSuspendAgent(a)}>
                        <Text className="text-white text-xs font-semibold">{t('super.suspend') ?? 'Suspend'}</Text>
                      </Button>
                    ) : (
                      <Button compact variant="success" style={GRID_ITEM} onPress={() => statusMutation.mutate({ id: a.adminUserId, status: 'RESUME' })}>
                        <Text className="text-white text-xs font-semibold">{t('super.resume') ?? 'Resume'}</Text>
                      </Button>
                    )}
                    {/* Delete is deliberately not a filled danger button: it sits
                        beside Suspend, which is the reversible action, and only
                        reads as destructive until the confirm dialog is opened. */}
                    <Button
                      compact
                      variant="ghost"
                      style={GRID_ITEM}
                      disabled={deleteMutation.isPending}
                      onPress={() => setDeleteAgent(a)}
                    >
                      <Text className="text-red-500 text-xs font-semibold">{t('super.delete') ?? 'Delete'}</Text>
                    </Button>
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
      {deleteAgent && (
        <DeleteAgentDialog
          agent={deleteAgent}
          busy={deleteMutation.isPending}
          onClose={() => setDeleteAgent(null)}
          onConfirm={() => deleteMutation.mutate(deleteAgent.adminUserId)}
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
  const { colors } = useTheme();
  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="font-bold" style={{ color: colors.textPrimary }}>
          {t('super.inviteAgentTitle') ?? 'New agent invite'}
        </Text>
        <Text className="text-sm" style={{ color: colors.textSecondary }}>
          {t('super.inviteAgentDesc') ?? 'Share this link with your agent. It works once, opened in Telegram.'}
        </Text>
        <View className="rounded-xl px-3 py-2" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}>
          <Text className="text-xs" style={{ color: colors.textSecondary }} numberOfLines={3}>
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
            <Text className="text-sm" style={{ color: colors.textPrimary }}>{t('super.close') ?? 'Close'}</Text>
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
  const { colors } = useTheme();
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
        <Text className="font-bold" style={{ color: colors.textPrimary }}>{t('super.warnAgent') ?? 'Warn agent'}</Text>
        <Text className="text-sm" style={{ color: colors.textSecondary }}>{agent.businessName ?? `@${agent.username ?? agent.adminUserId}`}</Text>
        <Text className="text-xs" style={{ color: colors.textSecondary }}>{t('super.reasonLabel') ?? 'Reason'}</Text>
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
            <Text className="text-sm" style={{ color: colors.textPrimary }}>{t('super.cancel') ?? 'Cancel'}</Text>
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
  const { colors } = useTheme();
  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="font-bold" style={{ color: colors.textPrimary }}>
          {t('super.suspendTitle', { name: agent.businessName ?? `@${agent.username ?? agent.adminUserId}` }) ?? 'Suspend agent?'}
        </Text>
        <Text className="text-sm" style={{ color: colors.textSecondary }}>
          {t('super.suspendDesc') ??
            "This will end all of the agent's active games and block their players from joining or playing until the agent is resumed."}
        </Text>
<View className="flex-row gap-2">
          <Button variant="danger" style={{ flex: 1 }} onPress={onConfirm}>
            <Text className="text-white text-sm font-semibold">{t('super.suspendBtn') ?? 'Suspend'}</Text>
          </Button>
          <Button variant="neutral" style={{ flex: 1 }} onPress={onClose}>
            <Text className="text-sm" style={{ color: colors.textPrimary }}>{t('super.cancel') ?? 'Cancel'}</Text>
          </Button>
        </View>
      </Card>
    </Modal>
  );
}

/**
 * Deleting an agent takes their players and their whole room with it: the tenant
 * database holding every game, card, wallet and transaction goes too. Nothing
 * here can be recovered afterwards, so the consequences are spelled out rather
 * than implied — and the server refuses the delete outright while a game is
 * running or a payment is still pending, which is what the last line warns about.
 */
function DeleteAgentDialog({
  agent,
  busy,
  onClose,
  onConfirm,
  t,
}: {
  agent: AgentResponse;
  busy: boolean;
  onClose: () => void;
  onConfirm: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const { colors } = useTheme();
  return (
    <Modal onClose={onClose}>
      <Card className="gap-3" style={{ borderColor: colors.danger + '66', borderWidth: 1 }}>
        <Text className="font-bold" style={{ color: colors.textPrimary }}>
          {t('super.deleteTitle', { name: agent.businessName ?? `@${agent.username ?? agent.adminUserId}` }) ??
            'Delete agent permanently?'}
        </Text>
        <Text className="text-sm" style={{ color: colors.textSecondary }}>
          {t('super.deleteDesc') ??
            "This permanently deletes the agent, every player registered under them, and their whole room — all games, cards, wallets and transaction history. It cannot be undone."}
        </Text>
        <Text className="text-xs" style={{ color: colors.textSecondary }}>
          {t('super.deleteRefusalHint') ??
            'If a game is still running or a payment is still pending, the delete is refused. Suspend the agent first, settle the payments, then delete.'}
        </Text>
        <View className="flex-row gap-2">
          <Button variant="danger" style={{ flex: 1 }} disabled={busy} onPress={onConfirm}>
            <Text className="text-white text-sm font-semibold">
              {busy ? (t('common.loading') ?? 'Loading…') : (t('super.deleteConfirmBtn') ?? 'Delete forever')}
            </Text>
          </Button>
          <Button variant="neutral" style={{ flex: 1 }} disabled={busy} onPress={onClose}>
            <Text className="text-sm" style={{ color: colors.textPrimary }}>{t('super.cancel') ?? 'Cancel'}</Text>
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
  const { colors } = useTheme();
  const isFetching = loading && !stats;
  const rows: { label: string; value: string; color?: string }[] = [
    { label: t('super.balance') ?? 'Balance', value: stats ? stats.balance.toLocaleString() : '—', color: colors.gold },
    { label: t('super.agentsWaiting') ?? 'Total Agents', value: stats ? String(stats.totalPlayers) : '—' },
    { label: t('super.barGames') ?? 'Games', value: stats ? String(stats.totalGames) : '—' },
    { label: t('super.completedStat') ?? 'Completed', value: stats ? String(stats.endedGames) : '—' },
    { label: t('super.transactions') ?? 'Transactions', value: stats ? String(stats.totalTransactions) : '—' },
    { label: t('super.commission') ?? 'Commission', value: stats ? stats.totalCommission.toLocaleString() : '—', color: colors.accent },
  ];

  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="font-bold" style={{ color: colors.textPrimary }}>{t('super.agentStats') ?? 'Agent statistics'}</Text>
        <Text className="text-sm" style={{ color: colors.textSecondary }}>
          {agent.businessName ?? `@${agent.username ?? agent.adminUserId}`}
        </Text>
        {isFetching ? (
          <Text className="text-sm" style={{ color: colors.textSecondary }}>{t('super.status') ?? 'Loading...'}</Text>
        ) : (
          <View className="flex-row flex-wrap gap-2">
            {rows.map((r) => (
              <View key={r.label} className="rounded-xl px-3 py-2" style={{ width: '48%', backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}>
                <Text className="text-[10px] uppercase" style={{ color: colors.textInactive }}>{r.label}</Text>
                <Text className="text-lg font-bold" style={{ color: r.color ?? colors.textPrimary }}>{r.value}</Text>
              </View>
            ))}
          </View>
        )}
        <Text className="text-xs mt-1" style={{ color: colors.textSecondary }}>{t('super.warningsLabel') ?? 'Warnings'}</Text>
        {warnings.length === 0 ? (
          <Text className="text-sm" style={{ color: colors.textSecondary }}>{t('super.noWarningsRecorded') ?? 'No warnings recorded.'}</Text>
        ) : (
          <View className="gap-2">
            {warnings.slice(0, 5).map((w) => (
              <View key={w.id} className="rounded-xl px-3 py-2" style={{ borderColor: colors.warning + '33', backgroundColor: colors.warning + '0d', borderWidth: 1 }}>
                <Text className="text-sm" style={{ color: colors.textPrimary }}>{w.reason}</Text>
                <Text className="text-xs mt-0.5" style={{ color: colors.textInactive }}>{new Date(w.createdAt).toLocaleDateString()}</Text>
              </View>
            ))}
          </View>
        )}
        <Button variant="neutral" onPress={onClose} style={{ marginTop: 8 }}>
          <Text className="text-sm" style={{ color: colors.textPrimary }}>{t('super.close') ?? 'Close'}</Text>
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
  const { colors } = useTheme();
  return (
    <Button
      variant={selected ? 'danger' : 'neutral'}
      onPress={onPress}
      style={{ paddingVertical: 10 }}
    >
      <Text className={selected ? 'text-white' : 'text-sm'} style={{ color: selected ? '#FFFFFF' : colors.textPrimary }}>{label}</Text>
    </Button>
  );
}