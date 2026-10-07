import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { getApiErrorMessage } from '@/api/client';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';

/** Biggest choice first, so the top of the stack is the largest circle. */
const CHOICES = [10, 5, 2, 1];
/** Every circle matches the smallest of the old sizes, so the targets stay uniform. */
const SIZE = 48;

interface CardCountFanProps {
  gameId: number;
  entryFee: number;
  onClose: () => void;
  onPreviewed?: () => void;
}

/**
 * Picks how many cards to hold, as circles fanned out above the add button
 * instead of a dialog. Each circle does the thing immediately, so there is no
 * second confirm step and nothing to dismiss before the cards appear.
 *
 * Nothing is charged here: the cards land on the game screen and the player
 * registers the ones they want, one at a time.
 */
export function CardCountFan({ gameId, entryFee, onClose, onPreviewed }: CardCountFanProps) {
  const t = useTranslate();
  const { colors } = useTheme();
  const [loadingCount, setLoadingCount] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const choose = async (count: number) => {
    setLoadingCount(count);
    setError(null);
    try {
      await gamesApi.previewCards(gameId, count);
      onPreviewed?.();
      onClose();
    } catch (e) {
      setError(getApiErrorMessage(e));
    } finally {
      setLoadingCount(null);
    }
  };

  return (
    <>
      <Pressable accessibilityElementsHidden onPress={onClose} className="absolute inset-0" />

      <View className="absolute bottom-24 right-4 items-end" style={{ gap: 8 }}>
        <Text className="text-[11px]" style={{ color: colors.textSecondary }}>
          {entryFee} {t('game.coinsPerCard') ?? 'birr per card'}
        </Text>

        {error ? (
          <View className="max-w-[220px] rounded-xl border px-3 py-2" style={{ borderColor: '#FF5C6C40', backgroundColor: '#FF5C6C15' }}>
            <Text className="text-[11px]" style={{ color: colors.danger }}>{error}</Text>
          </View>
        ) : null}

        {CHOICES.map((n) => {
          const busy = loadingCount != null;
          return (
            <Pressable
              key={n}
              onPress={() => void choose(n)}
              disabled={busy}
              accessibilityRole="button"
              accessibilityLabel={`${n} ${t('game.cards') ?? 'cards'}`}
              className="items-center justify-center rounded-full border-2 active:opacity-80"
              style={{
                width: SIZE,
                height: SIZE,
                borderColor: colors.primary,
                backgroundColor: colors.primary,
                opacity: busy ? (loadingCount === n ? 0.7 : 0.35) : 1,
              }}
            >
              <Text
                className="font-black text-white"
                style={{ fontSize: SIZE * 0.34 }}
              >
                {busy && loadingCount === n ? '…' : n}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </>
  );
}