import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { Button, Card, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { GameStatus, PlayerCardHistory } from '@/types';

export default function PlayerHistoryScreen() {
  const t = useTranslate();
  const [tab, setTab] = useState<'games' | 'wins'>('games');

  const { data, isFetching, refetch } = useQuery({
    queryKey: ['player/history/cards'],
    queryFn: () => gamesApi.getPlayerCardHistory(),
  });

  const history: PlayerCardHistory[] = (data?.data ?? []).filter(
    (h) => h.game.status === GameStatus.ENDED
  );
  const wins = history.filter((h) => h.cards.some((c) => c.winner) || h.win > 0);
  const rows = tab === 'wins' ? wins : history;

  return (
    <Screen>
      <ScreenHeader title={t('player.completedGamesTitle') ?? 'History'} />

      <View className="flex-row gap-2 mb-4">
        <Button variant={tab === 'games' ? 'primary' : 'outline'} onPress={() => setTab('games')} style={{ flex: 1 }}>
          {t('player.historyGamesTab') ?? 'Games'}
        </Button>
        <Button variant={tab === 'wins' ? 'primary' : 'outline'} onPress={() => setTab('wins')} style={{ flex: 1 }}>
          {t('player.historyWinsTab') ?? 'Wins'}
        </Button>
      </View>

      <FlatList
        data={rows}
        extraData={getClientLocale()}
        keyExtractor={(h) => String(h.game.id)}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={() => refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {tab === 'wins' ? (t('player.noWinsTitle') ?? 'No wins yet') : (t('player.noCompletedGames') ?? 'No completed games yet')}
            </Text>
          </Card>
        }
        renderItem={({ item }) => (
          <HistoryCard entry={item} t={t} />
        )}
      />
    </Screen>
  );
}

function HistoryCard({
  entry,
  t,
}: {
  entry: PlayerCardHistory;
  t: ReturnType<typeof useTranslate>;
}) {
  const game = entry.game;
  const wonCards = entry.cards.filter((c) => c.winner);
  return (
    <Card className="p-0 overflow-hidden gap-0">
      <View className="flex-row justify-between items-center p-4">
        <View>
          <Text className="text-bp-textPrimary font-semibold">
            {t('player.gameNumber', { id: String(game.id) }) ?? `Game #${game.id}`}
          </Text>
          <Text className="text-bp-textSecondary text-xs">
            {t('player.coinsPerCard', { fee: String(game.entryFee), count: String(entry.cards.length) }) ??
              `${entry.cards.length} cards · ${game.entryFee} each`}
          </Text>
        </View>
        <View className="items-end">
          <Text className="text-bp-textSecondary text-xs">
            {t('player.spent', { val: String(entry.bet) }) ?? `Spent ${entry.bet}`}
          </Text>
          {entry.win > 0 ? (
            <Text className="text-bp-accentInk text-sm font-bold">
              {t('player.won', { val: String(entry.win) }) ?? `+${entry.win}`}
            </Text>
          ) : null}
          <Text className={entry.net >= 0 ? 'text-bp-accentInk font-black' : 'text-bp-dangerInk font-black'}>
            {t('player.net', { val: `${entry.net >= 0 ? '+' : ''}${entry.net}` }) ?? `${entry.net >= 0 ? '+' : ''}${entry.net}`}
          </Text>
        </View>
      </View>

      <View className="border-t border-bp-borderInactive">
        {entry.cards.map((card) => (
          <View key={card.cardId} className="flex-row items-center gap-3 px-4 py-2.5">
            <View className="h-7 min-w-7 items-center justify-center rounded-lg border border-bp-borderInactive bg-bp-surfaceAlt px-1.5">
              <Text className="text-bp-textSecondary text-xs font-bold">#{card.cardId}</Text>
            </View>
            <View className="flex-1">
              <Text className="text-bp-textPrimary text-sm">
                {card.winner
                  ? (t('player.winningCard') ?? 'Winning card')
                  : card.banned
                    ? (t('player.bannedCard') ?? 'Banned')
                    : (t('player.played') ?? 'Played')}
              </Text>
              <Text className="text-bp-textSecondary text-[11px]">
                {t('player.registeredOn', { date: new Date(card.registeredAt).toLocaleDateString() })}
                {card.claimResult === 'REJECTED' && card.rejectionReason
                  ? ` · ${t('player.rejectedReason', { reason: card.rejectionReason }) ?? `Rejected: ${card.rejectionReason}`}`
                  : ''}
              </Text>
            </View>
            {card.winner ? (
              <Text className="rounded-full border border-bp-accent40 bg-bp-accent10 px-2.5 py-1 text-[10px] font-bold text-bp-accentInk">
                🏆 {t('player.winnerBadge') ?? 'Winner'}
              </Text>
            ) : card.banned ? (
              <Text className="rounded-full border border-bp-danger50 bg-bp-danger15 px-2.5 py-1 text-[10px] font-bold text-bp-dangerInk">
                {t('player.bannedBadge') ?? 'Banned'}
              </Text>
            ) : (
              <Text className="rounded-full border border-bp-borderInactive bg-bp-surfaceAlt px-2.5 py-1 text-[10px] font-bold text-bp-textSecondary">
                {t('player.played') ?? 'Played'}
              </Text>
            )}
          </View>
        ))}
      </View>

      {wonCards.length > 0 && (
        <View className="border-t border-bp-borderInactive bg-bp-accent10 px-4 py-2">
          <Text className="text-center text-[11px] font-bold text-bp-accentInk">
            🎉 {t('player.winningCardsSummary', { cards: wonCards.map((c) => `#${c.cardId}`).join(', ') }) ?? 'Winning cards'}
          </Text>
        </View>
      )}
    </Card>
  );
}