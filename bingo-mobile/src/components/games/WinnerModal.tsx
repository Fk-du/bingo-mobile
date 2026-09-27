import { Text, View } from 'react-native';
import { Button, Card, Modal } from '@/components/ui';
import { CardGrid } from '@/components/games/CardGrid';
import { WinnerBalloons } from '@/components/games/WinnerBalloons';
import { useTranslate } from '@/hooks/useTranslate';
import { PlayerCardView } from '@/types';

interface WinnerModalProps {
  visible: boolean;
  card: PlayerCardView | null;
  calledNumbers: number[];
  rewardAmount: number | null;
  /** Number of players sharing the pot, when the game had several winners. */
  winners: number | null;
  onClose: () => void;
}

/**
 * The winner's result, in one place: their winning card with every called number
 * marked, what they were paid, and — when several cards claimed at once — that the
 * pot was shared. The player sees the same card the admin reviewed, so the outcome
 * is never a bare "you won" with nothing to check.
 */
export function WinnerModal({
  visible,
  card,
  calledNumbers,
  rewardAmount,
  winners,
  onClose,
}: WinnerModalProps) {
  const t = useTranslate();
  if (!visible) return null;

  return (
    <Modal onClose={onClose}>
      <View>
        <WinnerBalloons />
        <Card className="gap-3 border-bp-gold50 bg-bp-surface">
          <View className="items-center gap-1">
            <Text className="text-2xl">🎉</Text>
            <Text className="text-center text-lg font-bold text-bp-goldInk">
              {t('game.winnerModalTitle') ?? 'Congratulations, you won!'}
            </Text>
            {rewardAmount != null && rewardAmount > 0 && (
              <Text className="text-center text-2xl font-bold text-emerald-500">
                {(t('game.winnerModalReward', { amount: rewardAmount.toFixed(2) }) as string) ??
                  `+${rewardAmount.toFixed(2)}`}
              </Text>
            )}
            {winners != null && winners > 1 && (
              <Text className="text-center text-xs text-bp-textSecondary">
                {(t('game.winnerModalShared', { count: winners }) as string) ??
                  `${winners} cards claimed Bingo — the pot was split equally between the winners.`}
              </Text>
            )}
          </View>

          {card && (
            <View className="gap-1">
              <Text className="text-center text-[10px] font-bold uppercase tracking-wider text-bp-textSecondary">
                {t('game.winnerModalCard') ?? 'Your winning card'}
              </Text>
              <CardGrid numbers={card.numbers} called={calledNumbers} />
            </View>
          )}

          <Button variant="primary" onPress={onClose}>
            {(t('common.close') ?? 'Close') as string}
          </Button>
        </Card>
      </View>
    </Modal>
  );
}
