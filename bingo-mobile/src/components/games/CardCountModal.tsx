import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { AppTextInput, Button, Card, FieldLabel, Modal } from '@/components/ui';
import { gamesApi } from '@/api';
import { getApiErrorMessage } from '@/api/client';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';

interface CardCountModalProps {
  gameId: number;
  entryFee: number;
  onClose: () => void;
  onPreviewed?: () => void;
}

/**
 * Asks how many cards to hold. A modal instead of fixed choices, so a player
 * can enter any amount instead of picking from a few preset sizes.
 *
 * Nothing is charged here: the cards land on the game screen and the player
 * registers the ones they want, one at a time.
 */
export function CardCountModal({ gameId, entryFee, onClose, onPreviewed }: CardCountModalProps) {
  const t = useTranslate();
  const { colors } = useTheme();
  const [count, setCount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const parsed = Number(count);
  const valid = count.trim() !== '' && Number.isInteger(parsed) && parsed >= 1;
  const total = valid ? parsed * entryFee : null;

  const choose = async () => {
    if (!valid || busy) return;
    setBusy(true);
    setError(null);
    try {
      await gamesApi.previewCards(gameId, parsed);
      onPreviewed?.();
      onClose();
    } catch (e) {
      setError(getApiErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <View className="flex-row items-center justify-between">
          <Text
            className="min-w-0 flex-1 text-base font-black"
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
            style={{ color: colors.gold }}
          >
            {t('game.howManyCards') ?? 'How many cards?'}
          </Text>
          <Pressable
            onPress={onClose}
            accessibilityRole="button"
            accessibilityLabel={t('game.closeWindow') ?? 'Close window'}
            className="ml-2 h-8 w-8 items-center justify-center rounded-full active:opacity-70"
            style={{ backgroundColor: colors.surfaceAlt }}
          >
            <Text className="text-base" style={{ color: colors.textSecondary }}>✕</Text>
          </Pressable>
        </View>

        <Text className="text-xs" style={{ color: colors.textSecondary }}>
          {entryFee} {t('game.coinsPerCard') ?? 'birr to enter (per card)'}
        </Text>

        <View>
          <FieldLabel>{t('game.enterCardCount') ?? 'Number of cards'}</FieldLabel>
          <AppTextInput
            value={count}
            onChangeText={setCount}
            keyboardType="number-pad"
            autoFocus
            placeholder={t('game.enterCardCount') ?? 'Number of cards'}
          />
        </View>

        {valid && total != null ? (
          <Text className="text-sm font-semibold" style={{ color: colors.primary }}>
            {t('game.cardTotal', { count: String(parsed), total: String(total) }) ??
              `${parsed} ${t('game.cards') ?? 'cards'} · ${total} birr`}
          </Text>
        ) : null}

        {error ? (
          <View className="rounded-xl border px-3 py-2" style={{ borderColor: '#FF5C6C40', backgroundColor: '#FF5C6C15' }}>
            <Text className="text-xs" style={{ color: colors.danger }}>{error}</Text>
          </View>
        ) : null}

        <Button variant="primary" onPress={() => void choose()} disabled={busy || !valid}>
          {busy ? '…' : (t('game.previewCards') ?? 'Preview cards')}
        </Button>
      </Card>
    </Modal>
  );
}