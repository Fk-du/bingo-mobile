import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { Button, Card, Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { getClientLocale } from '@/lib/clientTranslations';
import { GameStatus, PlayerCardHistory } from '@/types';

export default function PlayerHistoryScreen() {
  const t = useTranslate();
  const [tab, setTab] = useState<'games' | 'wins'>('games');
  const { colors } = useTheme();

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
      <ScreenHeader title={t('player.completedGamesTitle') ?? 'History'} left={<ScreenBackButton />} />

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
            <Text style={{ color: colors.textSecondary }} className="text-center">
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
  const { colors } = useTheme();
  const wonCards = entry.cards.filter((c) => c.winner);
  return (
    <Card className="p-0 overflow-hidden gap-0">
      <View className="flex-row justify-between items-center p-4">
        <View>
          <Text className="font-semibold" style={{ color: colors.textPrimary }}>
            {t('player.gameNumber', { id: String(game.id) }) ?? `Game #${game.id}`}
          </Text>
          <Text className="text-xs" style={{ color: colors.textSecondary }}>
            {t('player.coinsPerCard', { fee: String(game.entryFee), count: String(entry.cards.length) }) ??
              `${entry.cards.length} cards · ${game.entryFee} each`}
          </Text>
        </View>
        <View className="items-end">
          <Text className="text-xs" style={{ color: colors.textSecondary }}>
            {t('player.spent', { val: String(entry.bet) }) ?? `Spent ${entry.bet}`}
          </Text>
          {entry.win > 0 ? (
            <Text className="text-sm font-bold" style={{ color: colors.accent }}>
              {t('player.won', { val: String(entry.win) }) ?? `+${entry.win}`}
            </Text>
          ) : null}
          <Text className={entry.net >= 0 ? 'font-black' : 'font-black'} style={{ color: entry.net >= 0 ? colors.accent : colors.danger }}>
            {t('player.net', { val: `${entry.net >= 0 ? '+' : ''}${entry.net}` }) ?? `${entry.net >= 0 ? '+' : ''}${entry.net}`}
          </Text>
        </View>
      </View>

      <View className="border-t" style={{ borderColor: colors.borderInactive }}>
        {entry.cards.map((card) => (
          <View key={card.cardId} className="flex-row items-center gap-3 px-4 py-2.5">
            <View className="h-7 min-w-7 items-center justify-center rounded-lg border px-1.5" style={{ borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt }}>
              <Text className="text-xs font-bold" style={{ color: colors.textSecondary }}>#{card.cardId}</Text>
            </View>
            <View className="flex-1">
              <Text className="text-sm" style={{ color: colors.textPrimary }}>
                {card.winner
                  ? (t('player.winningCard') ?? 'Winning card')
                  : card.banned
                    ? (t('player.bannedCard') ?? 'Banned')
                    : (t('player.played') ?? 'Played')}
              </Text>
              <Text className="text-[11px]" style={{ color: colors.textSecondary }}>
                {t('player.registeredOn', { date: new Date(card.registeredAt).toLocaleDateString() })}
                {card.claimResult === 'REJECTED' && card.rejectionReason
                  ? ` · ${t('player.rejectedReason', { reason: card.rejectionReason }) ?? `Rejected: ${card.rejectionReason}`}`
                  : ''}
              </Text>
            </View>
            {card.winner ? (
              <Text className="rounded-full border px-2.5 py-1 text-[10px] font-bold" style={{ borderColor: '#36E4B540', backgroundColor: '#36E4B510', color: colors.accent }}>
                🏆 {t('player.winnerBadge') ?? 'Winner'}
              </Text>
            ) : card.banned ? (
              <Text className="rounded-full border px-2.5 py-1 text-[10px] font-bold" style={{ borderColor: '#FF5C6C50', backgroundColor: '#FF5C6C15', color: colors.danger }}>
                {t('player.bannedBadge') ?? 'Banned'}
              </Text>
            ) : (
              <Text className="rounded-full border px-2.5 py-1 text-[10px] font-bold" style={{ borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt, color: colors.textSecondary }}>
                {t('player.played') ?? 'Played'}
              </Text>
            )}
          </View>
        ))}
      </View>

      {wonCards.length > 0 && (
        <View className="border-t px-4 py-2" style={{ borderColor: colors.borderInactive, backgroundColor: '#36E4B510' }}>
          <Text className="text-center text-[11px] font-bold" style={{ color: colors.accent }}>
            🎉 {t('player.winningCardsSummary', { cards: wonCards.map((c) => `#${c.cardId}`).join(', ') }) ?? 'Winning cards'}
          </Text>
        </View>
      )}
    </Card>
  );
}