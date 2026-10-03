import { Pressable, Text, View } from 'react-native';
import { CardGrid } from '@/components/games/CardGrid';
import type { PreviewCardView, PlayerCardView } from '@/types';

type Tone = 'preview' | 'registered' | 'live' | 'winner' | 'banned';

interface CardTileProps {
  card: PlayerCardView | PreviewCardView;
  tone: Tone;
  t: (key: string, params?: Record<string, string | number>) => string | undefined;
  cardIdLabel: string;
  called?: number[];
  marked?: number[];
  lastCalledNumber?: number | null;
  interactive?: boolean;
  busy?: boolean;
  /** Multi-select mode: taps toggle selection; long-press enters it. */
  selectable?: boolean;
  selected?: boolean;
  onSelectToggle?: () => void;
  onLongPressCard?: () => void;
  onToggleMark?: (n: number) => void;
  onRegister?: () => void;
  onRemove?: () => void;
  onClaim?: () => void;
  markColor?: { fill: string; border: string };
  footer?: React.ReactNode;
}

/**
 * One card on the player's board.
 *
 * The header is the card's action area, and what it shows depends on where the
 * card is in its life:
 * - preview: "Register card" plus X, because the card is not paid for yet.
 * - registered (registration still open): "Registered" plus X, and X refunds.
 * - live: the Bingo button, so the claim sits with the card it claims.
 * - finished: a winner/banned badge and nothing to press.
 */
export function CardTile({
  card,
  tone,
  t,
  cardIdLabel,
  called = [],
  marked = [],
  lastCalledNumber,
  interactive = false,
  busy = false,
  selectable = false,
  selected = false,
  onSelectToggle,
  onLongPressCard,
  onToggleMark,
  onRegister,
  onRemove,
  onClaim,
  markColor,
  footer,
}: CardTileProps) {
  const dimmed = tone === 'banned';
  const status = renderStatus();
  const ringStyle = selected
    ? { borderColor: '#6B5BFF', borderWidth: 2, borderRadius: 14, boxShadow: '0 0 12px rgba(107,91,255,0.55)' }
    : undefined;

  return (
    <View className="gap-1.5" style={{ width: '48%', ...ringStyle }}>
      <View className={`flex-row items-center gap-1.5 ${dimmed ? 'justify-end' : 'justify-between'}`}>
        {/* A banned card shows its number at the bottom in red instead: the number
            is what an admin needs to identify it, and it reads as part of the
            penalty rather than as a neutral caption above a dimmed board. */}
        {!dimmed && (
          <Text className="shrink text-[9px] font-bold uppercase tracking-wider text-bp-textSecondary">
            {cardIdLabel}
          </Text>
        )}
        {status}
      </View>
      <View className={dimmed ? 'opacity-60' : ''}>
        <CardGrid
          numbers={card.numbers}
          called={called}
          marked={marked}
          lastCalledNumber={lastCalledNumber}
          interactive={interactive}
          onToggle={onToggleMark}
          selectable={selectable}
          selected={selected}
          onSelect={onSelectToggle}
          onLongPressCard={onLongPressCard}
          markColor={markColor}
        />
      </View>
      {dimmed && (
        <View className="items-center gap-0.5">
          <Text className="text-[11px] font-black uppercase tracking-wider text-red-500">{cardIdLabel}</Text>
          <Text className="text-center text-[10px] text-red-500/80">
            {t('game.bannedCardHint') ?? 'This card is banned.'}
          </Text>
        </View>
      )}
      {footer}
    </View>
  );

  function renderStatus() {
    if (selectable) {
      return (
        <Pressable
          onPress={onSelectToggle}
          hitSlop={6}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: selected }}
          className={`h-5 w-5 items-center justify-center rounded-full border ${
            selected ? 'border-bp-primary bg-bp-primary' : 'border-bp-borderInactive bg-bp-surfaceAlt'
          }`}
        >
          {selected ? <Text className="text-[10px] leading-none text-white">✓</Text> : null}
        </Pressable>
      );
    }
    switch (tone) {
      case 'preview':
        return (
          <View className="flex-row items-center gap-1">
            <Pressable
              onPress={onRegister}
              disabled={busy}
              className="rounded-full border border-bp-gold40 bg-bp-gold10 px-2 py-1 active:opacity-70"
            >
              <Text className="text-[9px] font-bold text-bp-goldInk">
                {busy
                  ? '…'
                  : t('game.registerCard') ?? 'Register card'}
              </Text>
            </Pressable>
            {onRemove ? <RemoveButton onPress={onRemove} disabled={busy} t={t} /> : null}
          </View>
        );
      case 'registered':
        return (
          <View className="flex-row items-center gap-1">
            <Text className="text-[9px] font-bold uppercase tracking-wider text-emerald-500">
              {t('game.registeredBadge') ?? 'Registered'}
            </Text>
            {onRemove ? <RemoveButton onPress={onRemove} disabled={busy} t={t} /> : null}
          </View>
        );
      case 'live':
        return (
          <Pressable
            onPress={onClaim}
            disabled={busy}
            style={{ boxShadow: '0 0 12px rgba(235,87,87,0.35)' }}
            className="rounded-full bg-bp-danger px-2.5 py-1 active:opacity-80"
          >
            <Text className="text-[9px] font-black tracking-[0.15em] text-white">
              {busy ? t('game.checking') ?? '✦ CHECKING…' : t('game.bingoBtn') ?? '✦ BINGO! ✦'}
            </Text>
          </Pressable>
        );
      case 'winner':
        return (
          <Text className="rounded-full border border-bp-success40 bg-bp-success10 px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider text-emerald-500">
            {t('game.winnerBadge') ?? 'Winner'}
          </Text>
        );
      case 'banned':
        return (
          <Text className="rounded-full border border-bp-danger50 bg-bp-danger15 px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider text-red-500">
            {t('game.bannedBadge') ?? 'Banned'}
          </Text>
        );
      default:
        return null;
    }
  }
}

/**
 * The X. On a preview it just drops the card; on a registered card it unregisters
 * it and refunds the entry fee, so it is only offered while registration is open.
 */
function RemoveButton({
  onPress,
  disabled,
  t,
}: {
  onPress?: () => void;
  disabled?: boolean;
  t: CardTileProps['t'];
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      hitSlop={6}
      accessibilityRole="button"
      accessibilityLabel={t('game.removeCard') ?? 'Remove card'}
      className="h-5 w-5 items-center justify-center rounded-full border border-bp-borderInactive bg-bp-surfaceAlt active:opacity-70"
    >
      <Text className="text-[10px] leading-none text-bp-textSecondary">✕</Text>
    </Pressable>
  );
}
