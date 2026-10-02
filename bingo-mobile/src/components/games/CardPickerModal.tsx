import { useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { gamesApi, walletApi } from '@/api';
import { getApiErrorMessage } from '@/api/client';
import { Button, Modal } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';

const QUICK_COUNTS = [1, 2, 5, 10];

interface CardPickerModalProps {
  gameId: number;
  entryFee: number;
  onClose: () => void;
  /** A set of cards was held for the player to look at. */
  onPreviewed?: () => void;
}

/**
 * Asks how many cards the player wants to see, then asks the server to hold
 * them. Nothing is charged here: the cards appear on the game screen, and the
 * player registers the ones they want one at a time.
 */
export function CardPickerModal({ gameId, entryFee, onClose, onPreviewed }: CardPickerModalProps) {
  const t = useTranslate();
  const [count, setCount] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [balance, setBalance] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    walletApi
      .get()
      .then((res) => {
        if (active) setBalance(res.data?.balance ?? null);
      })
      .catch(() => {
        if (active) setBalance(null);
      });
    return () => {
      active = false;
    };
  }, []);

  const totalCost = count * entryFee;
  // A preview is free, so this is a heads-up, not a block: the player may hold
  // cards they cannot afford yet and register them after a top-up.
  const shortfall = balance != null ? totalCost - balance : null;
  const canAffordAll = shortfall == null || shortfall <= 0;

  const preview = async () => {
    setLoading(true);
    setError(null);
    try {
      await gamesApi.previewCards(gameId, count);
      onPreviewed?.();
      onClose();
    } catch (e) {
      setError(getApiErrorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal onClose={onClose}>
      <View className="w-full max-h-[80%] bg-bp-surface rounded-3xl border border-bp-borderInactive overflow-hidden">
        <View className="flex-row justify-between items-center border-b border-bp-borderInactive px-4 py-2.5">
          <View className="min-w-0 flex-1 pr-2">
            <Text className="text-bp-textPrimary font-bold" numberOfLines={1}>
              {t('game.pickYourCard') ?? 'Add cards'}
            </Text>
            <Text className="text-bp-textSecondary text-[11px]" numberOfLines={1}>
              {entryFee} {t('game.coinsPerCard') ?? 'birr per card'}
            </Text>
          </View>
          <Pressable onPress={onClose} className="h-8 w-8 items-center justify-center rounded-full bg-bp-surfaceAlt">
            <Text className="text-bp-textSecondary">✕</Text>
          </Pressable>
        </View>

        {error && (
          <View className="mx-4 mt-3 rounded-xl border border-bp-danger40 bg-bp-danger15 px-3 py-2">
            <Text className="text-bp-dangerInk text-xs">{error}</Text>
          </View>
        )}

        {/* The body scrolls rather than being squeezed. The Amharic strings are
            long enough that a wrapped cost line, a balance and a shortfall
            warning can exceed the 80% cap, and with flexShrink on a plain View
            that content was simply clipped off the bottom. */}
        <ScrollView style={{ flexShrink: 1 }} className="px-4 py-3" showsVerticalScrollIndicator={false}>
          <Text className="text-bp-textSecondary text-[11px] font-semibold uppercase tracking-wider">
            {t('game.howManyCards') ?? 'How many cards?'}
          </Text>
          <View className="flex-row mt-2" style={{ gap: 8 }}>
            {QUICK_COUNTS.map((n) => {
              const active = count === n;
              return (
                <Pressable
                  key={n}
                  onPress={() => {
                    setError(null);
                    setCount(n);
                  }}
                  className={`flex-1 items-center rounded-2xl border py-2 ${
                    active ? 'border-bp-gold bg-bp-gold10' : 'border-bp-borderInactive bg-bp-surfaceAlt'
                  }`}
                >
                  <Text className={`text-base font-black ${active ? 'text-bp-goldInk' : 'text-bp-textSecondary'}`}>
                    {n}
                  </Text>
                  <Text className="text-[9px] text-bp-textSecondary mt-0.5">{t('game.cards') ?? 'cards'}</Text>
                </Pressable>
              );
            })}
          </View>

          <View className="mt-2.5 items-center rounded-xl border border-bp-borderInactive bg-bp-surfaceAlt px-3 py-2">
            {/* The cost is the number the player is actually deciding on, so it
                is the one line that gets full contrast. The reassurance about
                previews being free is supporting text and stays quiet. */}
            <Text className="text-[11px] text-bp-textSecondary">
              {t('game.previewIsFree') ?? 'Looking is free — you pay only for the cards you register.'}
            </Text>
            <Text className="mt-1 text-sm font-bold text-bp-textPrimary">
              {count} × {entryFee} = {totalCost} {t('game.coins') ?? 'birr'}
            </Text>
            {balance != null ? (
              <Text className="mt-0.5 text-[11px] text-bp-textSecondary">
                {t('game.yourBalance', { balance: String(balance) }) ?? `Balance: ${balance}`}
              </Text>
            ) : null}
          </View>

          {!canAffordAll && (
            <View className="mt-2 rounded-xl border border-bp-danger40 bg-bp-danger15 px-3 py-2">
              <Text className="text-[11px] font-semibold text-bp-dangerInk">
                {t('game.cannotRegisterAllYet', { shortfall: String(shortfall) }) ??
                  `You can see ${count} cards, but registering all of them needs ${shortfall} more coins.`}
              </Text>
            </View>
          )}
        </ScrollView>

        <View className="border-t border-bp-borderInactive bg-bp-background px-4 py-2.5">
          <Button variant="primary" disabled={loading} onPress={() => void preview()}>
            {loading
              ? t('game.loadingCards') ?? 'Holding cards…'
              : t('game.seeCards', { count: String(count) }) ?? `See ${count} cards`}
          </Button>
        </View>
      </View>
    </Modal>
  );
}
