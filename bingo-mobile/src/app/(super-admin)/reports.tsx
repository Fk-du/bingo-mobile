import { useQuery } from '@tanstack/react-query';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { reportsApi } from '@/api';
import { EmptyState, Metric, Screen, SectionHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { GameResponse, GameStatus } from '@/types';

export default function SuperAdminReportsScreen() {
  const t = useTranslate();

  const revenueQuery = useQuery({
    queryKey: ['super/reports/revenue'],
    queryFn: () => reportsApi.revenue(),
  });
  const gamesQuery = useQuery({
    queryKey: ['super/reports/games'],
    queryFn: () => reportsApi.games(),
  });

  const games: GameResponse[] = gamesQuery.data?.data ?? [];
  const revenue = revenueQuery.data?.data;
  const endedGames = games.filter((g) => g.status === GameStatus.ENDED);
  const totalFees = endedGames.reduce((sum, g) => sum + g.entryFee, 0);

  return (
    <Screen>
      <SectionHeader
        eyebrow={t('super.rpEyebrow') ?? ''}
        title={t('super.rpTitle') ?? 'Revenue & game history'}
        description={t('super.rpDesc') ?? ''}
      />

      <View className="flex-row flex-wrap gap-3 mb-4">
        <Metric
          label={t('super.totalRevenue') ?? 'Total Revenue'}
          value={revenueQuery.isLoading ? '...' : (revenue?.balance ?? 0).toLocaleString()}
          tone="gold"
        />
        <Metric
          label={t('super.ownerFees') ?? 'Owner Fees'}
          value={revenueQuery.isLoading ? '...' : (revenue?.platformFee ?? 0).toLocaleString()}
          tone="gold"
        />
        <Metric
          label={t('super.totalGames') ?? 'Total Games'}
          value={gamesQuery.isLoading ? '...' : games.length}
          tone="primary"
        />
        <Metric
          label={t('super.completedGames') ?? 'Completed Games'}
          value={gamesQuery.isLoading ? '...' : endedGames.length}
          tone="success"
        />
        <Metric
          label={t('super.totalEntryFees') ?? 'Total Entry Fees'}
          value={gamesQuery.isLoading ? '...' : totalFees.toLocaleString()}
          tone="default"
        />
      </View>

      <SectionHeader
        eyebrow={t('super.history') ?? ''}
        title={t('super.gameHistory') ?? 'Game History'}
        description={t('super.gameHistoryDesc') ?? ''}
      />

      <FlatList
        data={games}
        extraData={getClientLocale()}
        keyExtractor={(g) => String(g.id)}
        refreshControl={
          <RefreshControl
            refreshing={gamesQuery.isFetching}
            onRefresh={() => gamesQuery.refetch()}
            tintColor="#6B5BFF"
          />
        }
        contentContainerClassName="gap-2 pb-8"
        ListEmptyComponent={
          <EmptyState
            title={t('super.noGamesFound') ?? 'No games found'}
            description={t('super.noGamesFoundDesc') ?? 'Games will appear here once they are created.'}
          />
        }
        renderItem={({ item }) => (
          <View className="rounded-[18px] border border-bp-borderInactive bg-bp-surface60 px-4 py-3 gap-1.5">
            <View className="flex-row items-center justify-between">
              <Text className="text-bp-textPrimary font-semibold">#{item.id}</Text>
              <StatusPill status={item.status} />
            </View>
            <View className="flex-row justify-between mt-1">
              <Info label={t('super.entryFee') ?? 'Entry Fee'} value={item.entryFee} />
              <Info label={t('super.prizePool') ?? 'Prize Pool'} value={item.prizePool} />
              <Info label={t('super.maxPlayers') ?? 'Max Players'} value={item.maxPlayers} />
              <Info label={t('super.created') ?? 'Created'} value={new Date(item.createdAt).toLocaleDateString()} />
            </View>
          </View>
        )}
      />
    </Screen>
  );
}

function Info({ label, value }: { label: string; value: string | number }) {
  return (
    <View className="flex-1 items-start">
      <Text className="text-bp-textInactive text-[10px] uppercase">{label}</Text>
      <Text className="text-bp-textPrimary text-sm">{value}</Text>
    </View>
  );
}