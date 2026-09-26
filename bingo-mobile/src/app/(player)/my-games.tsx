import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { Button, Card, EmptyState, Screen, ScreenHeader, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { GameResponse } from '@/types';

export default function PlayerMyGamesScreen() {
  const t = useTranslate();
  const router = useRouter();

  const { data, isFetching, refetch } = useQuery({
    queryKey: ['player/my-games'],
    queryFn: () => gamesApi.getPlayerHistory(),
  });

  const games: GameResponse[] = data?.data ?? [];

  return (
    <Screen>
      <ScreenHeader title={t('player.myGamesTitle') ?? 'My games'} />

      <FlatList
        data={games}
        extraData={getClientLocale()}
        keyExtractor={(g) => String(g.id)}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={() => refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <EmptyState
            title={t('player.noGamesYet') ?? 'No games yet'}
            description={t('player.myGamesEmptyDesc') ?? 'Games you join will appear here.'}
          />
        }
        renderItem={({ item }) => {
          const open =
            item.status === 'REGISTRATION_OPEN' ||
            item.status === 'IN_PROGRESS' ||
            item.status === 'PAUSED' ||
            item.status === 'STARTING' ||
            item.status === 'CLAIM_PENDING';
          return (
            <Card className="gap-2">
              <View className="flex-row items-center justify-between">
                <Text className="text-bp-textPrimary font-semibold text-lg">
                  {t('game.gameNumber', { id: String(item.id) }) ?? `Game #${item.id}`}
                </Text>
                <StatusPill status={item.status} />
              </View>
              <View className="flex-row justify-between">
                <Text className="text-bp-textSecondary text-sm">
                  {t('admin.entryFee') ?? 'Entry'}: {item.entryFee}
                </Text>
                <Text className="text-bp-textSecondary text-sm">
                  {t('player.jackpotPool') ?? 'Pool'}: {item.prizePool}
                </Text>
                <Text className="text-bp-textSecondary text-sm">
                  {item.registeredPlayers ?? 0}/{item.maxPlayers}
                </Text>
              </View>
              <View className="flex-row justify-between items-center">
                <Text className="text-bp-textSecondary text-xs">
                  {new Date(item.createdAt ?? new Date().toISOString()).toLocaleDateString()}
                </Text>
                <Button
                  variant="outline"
                  onPress={() => router.push({ pathname: '/(player)/game/[id]', params: { id: String(item.id) } })}
                  style={{ paddingVertical: 6, paddingHorizontal: 12 }}
                >
                  <Text className="text-bp-primary text-xs font-semibold">
                    {open ? (t('player.openSeat') ?? 'Open') : (t('player.viewDetails') ?? 'Details')}
                  </Text>
                </Button>
              </View>
            </Card>
          );
        }}
      />
    </Screen>
  );
}