import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Button, Card, Modal } from '@/components/ui';
import { CardGrid } from '@/components/games/CardGrid';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { BannedCardView, WinnerCardView } from '@/types';

interface ResultsBoardProps {
  winnerCards: WinnerCardView[];
  bannedCards: BannedCardView[];
  calledNumbers: number[];
}

/**
 * The ended-game results board: every winning card of the round is listed and
 * opens its own card on tap, and every banned card is listed beneath. The whole
 * room sees the same two lists, so an outcome is never a bare headline.
 */
export function ResultsBoard({ winnerCards, bannedCards, calledNumbers }: ResultsBoardProps) {
  const t = useTranslate();
  const { colors } = useTheme();
  const [openWinner, setOpenWinner] = useState<WinnerCardView | null>(null);

  return (
    <View className="gap-3">
      <Card className="gap-2" style={{ borderColor: '#8B5E3C40', backgroundColor: '#8B5E3C10' }}>
        <Text className="text-center text-xs font-bold uppercase tracking-[0.16em]" style={{ color: colors.gold }}>
          {t('game.resultsWinners') ?? 'Winners'}
        </Text>
        {winnerCards.length === 0 ? (
          <Text className="text-center text-sm" style={{ color: colors.textSecondary }}>
            {t('game.resultsNoWinners') ?? 'No winning cards this game.'}
          </Text>
        ) : (
          winnerCards.map((winner) => (
            <Pressable
              key={winner.cardId}
              onPress={() => setOpenWinner(winner)}
              className="flex-row items-center justify-between rounded-xl border px-3 py-2 active:opacity-80"
              style={{ borderColor: '#8B5E3C30', backgroundColor: colors.surface }}
            >
              <Text className="text-sm font-bold" style={{ color: colors.textPrimary }}>
                {t('game.cardNumber', { id: String(winner.cardId) }) ?? `Card #${winner.cardId}`}
              </Text>
              <Text className="text-sm font-bold text-emerald-500">
                {winner.rewardAmount != null && winner.rewardAmount > 0
                  ? `+${winner.rewardAmount.toFixed(2)}`
                  : t('game.resultsWinnerTap') ?? 'Winner'}
              </Text>
            </Pressable>
          ))
        )}
      </Card>

      {bannedCards.length > 0 && (
        <Card className="gap-2" style={{ borderColor: '#FF5C6C40', backgroundColor: '#FF5C6C10' }}>
          <Text className="text-center text-xs font-bold uppercase tracking-[0.16em] text-red-500">
            {t('game.resultsBanned') ?? 'Banned'}
          </Text>
          <View className="flex-row flex-wrap" style={{ gap: 8 }}>
            {bannedCards.map((card) => (
              <View
                key={card.cardId}
                className="rounded-full border px-2.5 py-1"
                style={{ borderColor: '#FF5C6C40', backgroundColor: colors.surface }}
              >
                <Text className="text-xs font-bold" style={{ color: colors.textPrimary }}>
                  {t('game.cardNumber', { id: String(card.cardId) }) ?? `Card #${card.cardId}`}
                </Text>
              </View>
            ))}
          </View>
        </Card>
      )}

      {openWinner && (
        <Modal onClose={() => setOpenWinner(null)}>
          <Card className="gap-3">
            <View className="items-center gap-1">
              <Text className="text-2xl">🎉</Text>
              <Text className="text-center text-base font-bold" style={{ color: colors.gold }}>
                {t('game.resultsWinnerTitle', { id: String(openWinner.cardId) }) ??
                  `Winning card #${openWinner.cardId}`}
              </Text>
              {openWinner.rewardAmount != null && openWinner.rewardAmount > 0 && (
                <Text className="text-center text-xl font-bold text-emerald-500">
                  +{openWinner.rewardAmount.toFixed(2)}
                </Text>
              )}
            </View>
            <CardGrid numbers={openWinner.numbers} called={calledNumbers} />
            <Button variant="primary" onPress={() => setOpenWinner(null)}>
              {t('common.close') ?? 'Close'}
            </Button>
          </Card>
        </Modal>
      )}
    </View>
  );
}