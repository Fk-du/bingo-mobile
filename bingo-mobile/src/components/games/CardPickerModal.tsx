import { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { gamesApi, walletApi } from '@/api';
import { Button, Modal } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';

const QUICK_COUNTS = [1, 2, 5, 10];

interface CardPickerModalProps {
  gameId: number;
  entryFee: number;
  onClose: () => void;
  onRegistered?: () => void;
  onFailed?: () => void;
}

export function CardPickerModal({ gameId, entryFee, onClose, onRegistered, onFailed }: CardPickerModalProps) {
  const t = useTranslate();
  const [count, setCount] = useState(1);
  const [registering, setRegistering] = useState(false);
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
  const shortfall = balance != null ? totalCost - balance : null;

  const pick = async (n: number) => {
    setRegistering(true);
    setError(null);
    try {
      await gamesApi.register(gameId, n);
      onRegistered?.();
      onClose();
    } catch (e) {
      setError((e as { userMessage?: string }).userMessage ?? 'Registration failed');
      onFailed?.();
    } finally {
      setRegistering(false);
    }
  };

  return (
    <Modal onClose={onClose}>
      <View className="w-full max-h-[80%] bg-bp-surface rounded-3xl border border-bp-borderInactive overflow-hidden">
        <View className="flex-row justify-between items-center border-b border-bp-borderInactive px-4 py-3">
          <View>
            <Text className="text-bp-textPrimary font-bold">{t('game.pickYourCard') ?? 'Add cards'}</Text>
            <Text className="text-bp-textSecondary text-xs">
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

        <View style={{ flexShrink: 1 }} className="px-4 py-4">
          <Text className="text-bp-textSecondary text-xs font-semibold uppercase tracking-wider">
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
                  className={`flex-1 items-center rounded-2xl border py-2.5 ${
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
          <View className="mt-3 flex-row items-center justify-center rounded-xl border border-bp-gold30 bg-bp-gold10 px-3 py-2">
            <Text className="text-sm font-black text-bp-goldInk">
              {count} × {entryFee} = {totalCost}
            </Text>
            {balance != null && (
              <Text className="text-xs text-bp-textSecondary ml-2">
                {t('game.yourBalance', { balance: String(balance) }) ?? `Balance: ${balance}`}
              </Text>
            )}
          </View>
          {shortfall != null && shortfall > 0 && (
            <View className="mt-2 rounded-xl border border-bp-danger40 bg-bp-danger15 px-3 py-2">
              <Text className="text-xs font-semibold text-bp-dangerInk">
                {t('game.needMoreCoins', { shortfall: String(shortfall) }) ??
                  `You need ${shortfall} more coins. Request coins from your agent to join.`}
              </Text>
            </View>
          )}
        </View>

        <View className="border-t border-bp-borderInactive bg-bp-background px-4 py-3">
          <Button
            variant="primary"
            disabled={registering || (shortfall != null && shortfall > 0)} onPress={() => void pick(count)}>
            {registering
              ? t('game.registering') ?? 'Registering…'
              : `${t('game.totalCost', { fee: String(count * entryFee) }) ?? `Total: ${count * entryFee}`} — ${
                  t('game.registerCards', { count: String(count) }) ?? `Get ${count} cards`
                }`}
          </Button>
        </View>
      </View>
    </Modal>
  );
}