import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { FlatList, RefreshControl, Text, View } from 'react-native';
import { coinsApi, gamesApi, withdrawalsApi } from '@/api';
import { Button, Card, Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { getClientLocale } from '@/lib/clientTranslations';
import { GameStatus, PlayerCardHistory, CoinRequestResponse, WithdrawalResponse, RequestStatus } from '@/types';
import { PaymentProof } from '@/components/PaymentProof';

const STATUS_COLOR: Record<RequestStatus, string> = {
  PENDING: '#f59e0b',
  APPROVED: '#6B5BFF',
  REJECTED: '#FF5C6C',
  CANCELLED: '#9ca3af',
};

export default function PlayerHistoryScreen() {
  const t = useTranslate();
  const [tab, setTab] = useState<'games' | 'wins'>('games');
  const { colors } = useTheme();

  const { data, isFetching, refetch } = useQuery({
    queryKey: ['player/history/cards'],
    queryFn: () => gamesApi.getPlayerCardHistory(),
  });

  const { data: coinRequestsData } = useQuery({
    queryKey: ['wallet', 'coin-requests'],
    queryFn: () => coinsApi.getRequests(),
  });
  const { data: withdrawalsData } = useQuery({
    queryKey: ['wallet', 'withdrawals'],
    queryFn: () => withdrawalsApi.list(),
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
        ListFooterComponent={
          <TransactionHistory
            coinRequests={coinRequestsData?.data ?? []}
            withdrawals={withdrawalsData?.data ?? []}
            colors={colors}
            t={t}
          />
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

type TxItem = {
  id: number;
  kind: 'topup' | 'withdraw';
  amount: number;
  status: RequestStatus;
  rejectionReason: string | null;
  screenshotUrl: string | null;
  createdAt: string;
};

function TransactionHistory({
  coinRequests,
  withdrawals,
  colors,
  t,
}: {
  coinRequests: CoinRequestResponse[];
  withdrawals: WithdrawalResponse[];
  colors: ReturnType<typeof useTheme>['colors'];
  t: ReturnType<typeof useTranslate>;
}) {
  const transactions: TxItem[] = [
    ...coinRequests.map((r) => ({
      id: r.id,
      kind: 'topup' as const,
      amount: r.amount,
      status: r.status,
      rejectionReason: r.rejectionReason,
      screenshotUrl: r.screenshotUrl,
      createdAt: r.createdAt,
    })),
    ...withdrawals.map((w) => ({
      id: w.id,
      kind: 'withdraw' as const,
      amount: w.amount,
      status: w.status,
      rejectionReason: w.rejectionReason,
      screenshotUrl: null,
      createdAt: w.createdAt,
    })),
  ].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  if (transactions.length === 0) return null;

  return (
    <Card className="gap-3 mt-4">
      <Text className="text-xs font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
        {t('player.recentTransactions') ?? 'Recent Transactions'}
      </Text>
      {transactions.map((tx) => (
        <View
          key={`${tx.kind}-${tx.id}`}
          className="flex-row items-center justify-between rounded-xl px-3 py-2.5"
          style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}
        >
          <View className="flex-1 pr-3">
            <Text className="text-sm" style={{ color: colors.textPrimary }}>
              {tx.kind === 'topup'
                ? (t('mobile.topUpRequest') ?? 'Top-up request')
                : (t('mobile.withdrawalRequest') ?? 'Withdrawal request')}
            </Text>
            <Text className="text-xs" style={{ color: colors.textSecondary }}>
              {new Date(tx.createdAt).toLocaleDateString()}
            </Text>
            {tx.status === 'REJECTED' && tx.rejectionReason ? (
              <Text className="text-xs" style={{ color: colors.danger }} numberOfLines={1}>
                {t('player.rejectedReason', { reason: tx.rejectionReason }) ?? `Rejected: ${tx.rejectionReason}`}
              </Text>
            ) : null}
            {tx.kind === 'topup' && tx.screenshotUrl ? (
              <View className="mt-1 w-10 h-10 rounded-lg overflow-hidden">
                <PaymentProof url={tx.screenshotUrl} size={40} />
              </View>
            ) : null}
          </View>
          <View className="items-end gap-1">
            <Text className="text-sm font-bold" style={{ color: tx.kind === 'topup' ? colors.primary : colors.danger }}>
              {tx.kind === 'topup' ? `+${tx.amount.toLocaleString()}` : `-${tx.amount.toLocaleString()}`}
            </Text>
            <View className="rounded-full px-2 py-0.5" style={{ backgroundColor: colors.surfaceAlt }}>
              <Text className="text-[10px] font-semibold" style={{ color: STATUS_COLOR[tx.status] }}>
                {t(`status.${tx.status}`) ?? tx.status}
              </Text>
            </View>
          </View>
        </View>
      ))}
    </Card>
  );
}