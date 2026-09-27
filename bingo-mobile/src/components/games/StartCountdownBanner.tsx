import { Text, View } from 'react-native';
import { useTranslate } from '@/hooks/useTranslate';
import { GameStatus } from '@/types';

interface StartCountdownProps {
  status: GameStatus | null;
  reason: string | null;
  seconds: number;
}

/**
 * The notice every player sees while a game counts down, whether the game is
 * starting for the first time or coming back after a pause, a rejected claim or a
 * restart. Without it a resumed game looks broken: the board just stops updating.
 */
export function StartCountdownBanner({ status, reason, seconds }: StartCountdownProps) {
  const t = useTranslate();
  if (status !== GameStatus.STARTING) return null;

  const label =
    reason === 'resume'
      ? (t('game.countdownResuming') ?? 'Resuming in')
      : reason === 'restart'
        ? (t('game.countdownRestarting') ?? 'New round in')
        : reason === 'claim_resolved'
          ? (t('game.countdownContinuing') ?? 'Continuing in')
          : (t('game.countdownStarting') ?? 'Starting in');

  const hint = t('game.countdownHint') ?? 'Nothing to do — the numbers continue on their own.';

  return (
    <View className="items-center gap-1 rounded-2xl border border-bp-gold50 bg-bp-gold20 px-4 py-3">
      <View className="flex-row items-baseline gap-2">
        <Text className="text-4xl font-bold text-bp-goldInk">{seconds}</Text>
        <Text className="text-base font-bold text-bp-goldInk">{label}</Text>
      </View>
      <Text className="text-center text-xs text-bp-goldInk/80">{hint}</Text>
    </View>
  );
}
