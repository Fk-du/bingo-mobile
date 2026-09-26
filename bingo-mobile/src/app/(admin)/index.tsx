import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Alert, FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { useState } from 'react';
import { gamesApi, reportsApi } from '@/api';
import { Button, Card, Screen, ScreenHeader, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { GameResponse, GameStatus } from '@/types';

export default function AdminDashboardScreen() {
  const t = useTranslate();
  const router = useRouter();
  const [busyId, setBusyId] = useState<number | null>(null);

  const gamesQuery = useQuery({ queryKey: ['admin/games'], queryFn: () => gamesApi.getActive() });
  const metricsQuery = useQuery({ queryKey: ['admin/metrics'], queryFn: () => reportsApi.dashboard() });

  const games: GameResponse[] = gamesQuery.data?.data ?? [];
  const metrics = metricsQuery.data?.data as
    | Record<string, unknown>
    | undefined;

  const pendingCount = games.reduce(
    (sum, g) => sum + (Number((g as { pendingClaims?: number }).pendingClaims) || 0),
    0
  );

  const start = async (id: number) => {
    setBusyId(id);
    try {
      await gamesApi.start(id);
      await gamesQuery.refetch();
    } catch (e) {
      Alert.alert((e as { userMessage?: string }).userMessage ?? 'Failed to start');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('admin.gamesManageTitle') ?? 'Game management'} />

      <View className="flex-row gap-3 mb-4">
        <QuickStat label={t('admin.totalGamesMetric') ?? 'Games'} value={String(metrics?.totalGames ?? games.length)} />
        <QuickStat
          label={t('admin.pendingClaims') ?? 'Claims'}
          value={String(pendingCount)}
          accent
        />
        <QuickStat label={t('admin.playersMetric') ?? 'Players'} value={String(metrics?.players ?? '—')} />
      </View>

      <FlatList
        data={games}
        extraData={getClientLocale()}
        keyExtractor={(g) => String(g.id)}
        refreshControl={
          <RefreshControl refreshing={gamesQuery.isFetching} onRefresh={() => gamesQuery.refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListHeaderComponent={
          <View className="gap-3 mb-2">
            <Pressable
              onPress={() => router.push('/(admin)/new-game')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center bg-bp-primary15 border-bp-primary40">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.createNewGame') ?? 'Create new game'}</Text>
                <Text className="text-bp-primary">＋</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/players')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.playersTitle') ?? 'Player registry'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/coins')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.topUpTitle') ?? 'Top-up approvals'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/withdrawals')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.wdTitle') ?? 'Payout requests'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/cards')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.cardsTitle') ?? 'Card pool'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/broadcast')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.broadcastTitle') ?? 'Broadcast'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/profile')}
              className="active:opacity-80"
            >
              <Card>
                <View className="flex-row justify-between items-center">
                  <View className="gap-0.5">
                    <Text className="text-bp-textPrimary font-semibold">
                      {t('admin.profileTitle') ?? 'Profile'}
                    </Text>
                    <Text className="text-bp-textSecondary text-xs">
                      {t('admin.profileMenuDesc') ?? 'Invite players · Deposit account · Language'}
                    </Text>
                  </View>
                  <Text className="text-bp-primary">›</Text>
                </View>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/owner-fees')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.ofTitle') ?? 'Owner fees'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/notifications')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('mobile.notifications') ?? 'Notifications'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Text className="text-bp-textSecondary text-xs uppercase tracking-wider mt-2">
              {t('admin.activeGames') ?? 'Active games'}
            </Text>
          </View>
        }
        ListEmptyComponent={
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {t('admin.noGamesYet') ?? 'No games yet. Create your first game!'}
            </Text>
          </Card>
        }
        renderItem={({ item }) => {
          const open = item.status === GameStatus.REGISTRATION_OPEN;
          return (
            <Card className="gap-2">
              <View className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold text-lg">
                  {t('game.gameNumber', { id: String(item.id) }) ?? `Game #${item.id}`}
                </Text>
                <Text className="text-bp-accentInk">{t(`status.${item.status}`) ?? item.status}</Text>
              </View>
              {open ? (
                <View className="flex-row justify-between">
                  <Subtitle>
                    {t('admin.entryFee') ?? 'Entry'}: {item.entryFee}
                  </Subtitle>
                  <Subtitle>
                    {t('player.jackpotPool') ?? 'Pool'}: {item.prizePool}
                  </Subtitle>
                  <Subtitle>
                    {item.registeredPlayers ?? 0}/{item.maxPlayers}
                  </Subtitle>
                </View>
              ) : null}
              <View className="flex-row gap-2">
                {open && (
                  <Button variant="primary" onPress={() => void start(item.id)} disabled={busyId != null}>
                    {busyId === item.id ? t('admin.working') ?? 'Working…' : t('admin.start') ?? 'Start Game'}
                  </Button>
                )}
                <Button
                  variant="outline"
                  onPress={() => router.push({ pathname: '/(admin)/game/[id]', params: { id: String(item.id) } })}
                >
                  {t('admin.manage') ?? 'Manage'}
                </Button>
              </View>
            </Card>
          );
        }}
      />
    </Screen>
  );
}

function QuickStat({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <Card className="flex-1 p-3 items-center">
      <Title className={`text-xl ${accent ? 'text-bp-primary' : ''}`}>{value}</Title>
      <Subtitle className="text-xs text-center">{label}</Subtitle>
    </Card>
  );
}