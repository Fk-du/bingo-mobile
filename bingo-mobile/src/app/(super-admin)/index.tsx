import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { agentsApi, cardsApi, reportsApi } from '@/api';
import { Button, Card, EmptyState, Metric, Screen, SectionHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { AgentResponse, CardRequestResponse, AdminGameResponse, GameStatus } from '@/types';

const BAR_HEIGHT = 128;

export default function SuperAdminDashboardScreen() {
  const { colors } = useTheme();
  const t = useTranslate();
  const router = useRouter();

  const agentsQuery = useQuery({ queryKey: ['super/agents'], queryFn: () => agentsApi.list() });
  const gamesQuery = useQuery({ queryKey: ['super/games'], queryFn: () => reportsApi.games() });
  const cardsQuery = useQuery({
    queryKey: ['super/cards/requests'],
    queryFn: () => cardsApi.getCardRequests(),
    refetchInterval: 15_000,
  });

  const agents: AgentResponse[] = agentsQuery.data?.data ?? [];
  const games: AdminGameResponse[] = gamesQuery.data?.data ?? [];
  const cardRequests: CardRequestResponse[] = cardsQuery.data?.data ?? [];

  const activeAgents = agents.filter((a) => a.active);
  const pendingApproval = agents.filter((a) => !a.approved && a.active);
  const pendingCardRequests = cardRequests.filter((r) => r.status === 'PENDING');
  const endedGames = games.filter((g) => g.status === GameStatus.ENDED);
  const inProgressGames = games.filter((g) => g.status === GameStatus.IN_PROGRESS);
  const totalEntryFees = endedGames.reduce((sum, g) => sum + g.entryFee, 0);

  const agentName = (a: AgentResponse) =>
    a.businessName ??
    (a.username ? `@${a.username}` : (t('super.agentNumber', { id: String(a.adminUserId) }) ?? `Agent #${a.adminUserId}`));

  const bars = [
    { label: t('super.barAgents') ?? 'Agents', value: agents.length, color: colors.primary + '60' },
    { label: t('super.barGames') ?? 'Games', value: games.length, color: colors.warning + '60' },
    { label: t('super.barLive') ?? 'Live', value: inProgressGames.length, color: colors.gold + '60' },
    { label: t('super.barEnded') ?? 'Ended', value: endedGames.length, color: colors.success + '60' },
  ];
  const maxBar = Math.max(1, ...bars.map((b) => b.value));

  return (
    <Screen>
      <SectionHeader
        eyebrow={t('super.dbEyebrow') ?? ''}
        title={t('super.dbTitle') ?? 'Platform Overview'}
        description={t('super.dbDesc') ?? ''}
      />

      <ScrollView contentContainerClassName="gap-4 pb-8">
        <View className="flex-row flex-wrap gap-3">
          <Pressable
            onPress={() => router.push('/(super-admin)/agents')}
            className="active:opacity-80"
            style={{ width: '48%' }}
          >
            <Metric
              label={t('super.totalAgents') ?? 'Total Agents'}
              value={agentsQuery.isLoading ? '...' : agents.length}
              tone="primary"
              note={t('super.activeCount', { count: activeAgents.length }) ?? `${activeAgents.length} active`}
            />
          </Pressable>
          <Metric
            label={t('super.barGames') ?? 'Games'}
            value={gamesQuery.isLoading ? '...' : games.length}
            tone="warning"
            note={t('super.completedCount', { count: endedGames.length }) ?? `${endedGames.length} completed`}
          />
          <Metric
            label={t('super.liveGames') ?? 'Live Games'}
            value={gamesQuery.isLoading ? '...' : inProgressGames.length}
            tone="success"
          />
          <Pressable
            onPress={() => router.push('/(super-admin)/agents')}
            className="active:opacity-80"
            style={{ width: '48%' }}
          >
            <Metric
              label={t('super.pendingApprovals') ?? 'Pending Approvals'}
              value={pendingApproval.length}
              tone="gold"
              note={
                pendingApproval.length > 0
                  ? (t('super.agentsWaiting', { count: pendingApproval.length }) ?? `${pendingApproval.length} waiting`)
                  : (t('super.allApproved') ?? 'All approved')
              }
            />
          </Pressable>
          <Pressable
            onPress={() => router.push('/(super-admin)/cards')}
            className="active:opacity-80"
            style={{ width: '48%' }}
          >
            <Metric
              label={t('super.scEyebrow') ?? 'Cards'}
              value={cardsQuery.isLoading ? '...' : pendingCardRequests.length}
              tone="warning"
              note={
                pendingCardRequests.length > 0
                  ? (t('super.pendingCardPoolGrowth') ?? 'pending pool growth')
                  : (t('super.noPendingRequests') ?? 'no pending requests')
              }
            />
          </Pressable>
        </View>

        <Card>
          <Text className="text-sm font-semibold" style={{ color: colors.textPrimary }}>{t('super.platformSummary') ?? 'Platform Summary'}</Text>
          <Text className="text-xs mt-1" style={{ color: colors.textSecondary }}>{t('super.aggregateMetrics') ?? 'Aggregate metrics'}</Text>
          <View className="flex-row items-end justify-between mt-4" style={{ height: BAR_HEIGHT }}>
            {bars.map((b) => (
              <View key={b.label} className="flex-1 items-center mx-1">
                <Text className="text-xs font-medium mb-1" style={{ color: colors.textPrimary }}>{b.value}</Text>
                <View
                  className="w-full rounded-t-md"
                  style={{ height: Math.max(b.value > 0 ? 8 : 2, (b.value / maxBar) * (BAR_HEIGHT - 40)), backgroundColor: b.color }}
                />
                <Text className="text-[10px] mt-1 text-center" style={{ color: colors.textSecondary }}>{b.label}</Text>
              </View>
            ))}
          </View>
        </Card>

        <Card>
          <Text className="text-[11px] font-medium uppercase tracking-[0.18em]" style={{ color: colors.textSecondary }}>
            {t('super.entryFeesCollected') ?? 'Entry Fees Collected'}
          </Text>
          <Text className="text-2xl font-bold mt-1.5" style={{ color: colors.gold }}>
            {gamesQuery.isLoading ? '...' : totalEntryFees.toLocaleString()}
          </Text>
          <Text className="text-xs mt-1" style={{ color: colors.textSecondary }}>
            {t('super.fromCompletedGames', { count: endedGames.length }) ?? `from ${endedGames.length} completed games`}
          </Text>
        </Card>

        <Card>
          <View className="flex-row items-center justify-between">
            <Text className="text-sm font-semibold" style={{ color: colors.textPrimary }}>{t('super.recentAgents') ?? 'Recent Agents'}</Text>
            <Pressable onPress={() => router.push('/(super-admin)/agents')} hitSlop={8}>
              <Text className="text-sm" style={{ color: colors.primary }}>{t('super.viewAll') ?? 'View all'}</Text>
            </Pressable>
          </View>
          {agents.length === 0 ? (
            <View className="mt-3">
              <EmptyState
                title={t('super.noAgentsYet') ?? 'No agents yet'}
                description={t('super.noAgentsYetDesc') ?? 'Create an invite from the Agents page.'}
              />
            </View>
          ) : (
            <View className="gap-2 mt-3">
              {agents.slice(0, 5).map((a) => (
                <View
                  key={a.adminUserId}
                  className="flex-row items-center justify-between rounded-xl border px-3 py-2.5"
                  style={{ backgroundColor: colors.bg, borderColor: colors.borderInactive }}
                >
                  <View className="flex-1 pr-2">
                    <Text className="text-sm font-medium" style={{ color: colors.textPrimary }}>{agentName(a)}</Text>
                    <Text className="text-xs" style={{ color: colors.textSecondary }}>
                      {t('super.balanceColon') ?? 'Balance'}: {a.balance.toLocaleString()}
                    </Text>
                  </View>
                  {a.approved ? (
                    <View className="rounded-full px-2 py-0.5" style={{ backgroundColor: colors.success + '20' }}>
                      <Text className="text-[10px] font-bold uppercase tracking-wider" style={{ color: colors.success }}>
                        {t('super.approvedBadge') ?? 'Approved'}
                      </Text>
                    </View>
                  ) : (
                    <View className="rounded-full px-2 py-0.5" style={{ backgroundColor: colors.warning + '20' }}>
                      <Text className="text-[10px] font-bold uppercase tracking-wider" style={{ color: colors.warning }}>
                        {t('super.pendingBadge') ?? 'Pending'}
                      </Text>
                    </View>
                  )}
                </View>
              ))}
            </View>
          )}
        </Card>

        <Button variant="ghost" onPress={() => router.push('/(super-admin)/notifications')} style={{ alignSelf: 'flex-start' }}>
          {t('mobile.notifications') ?? 'Notifications'}
        </Button>
      </ScrollView>
    </Screen>
  );
}