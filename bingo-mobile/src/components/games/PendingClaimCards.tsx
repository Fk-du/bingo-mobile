import { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Card } from '@/components/ui';
import { CardGrid } from '@/components/games/CardGrid';
import { useTranslate } from '@/hooks/useTranslate';
import { PendingClaimCard } from '@/types';

/** A claim this fresh is still on screen as "just claimed" rather than a clock time. */
const JUST_CLAIMED_MS = 60_000;

/** Local wall-clock time of the claim, or null while it is still "just claimed". */
function claimedTime(claimedAt: string | null | undefined, now: number | null): string | null {
  if (!claimedAt) return null;
  const at = Date.parse(claimedAt);
  if (Number.isNaN(at)) return null;
  if (now != null && now - at < JUST_CLAIMED_MS) return null;
  return new Date(at).toLocaleTimeString();
}

interface PendingClaimCardsProps {
  claims: PendingClaimCard[];
  /** The signed-in player, so their own claim reads differently from someone else's. */
  currentPlayerId?: number | null;
  /** Put the panel away without touching the game; the next claim brings it back. */
  onDismiss: () => void;
}

/**
 * Cards that have claimed Bingo and are waiting for the admin, shown with every
 * called number marked. The claim is never a bare "someone won": each player sees
 * the exact card under review, so the pause can be checked instead of trusted, and
 * the claimant can confirm it is the card they meant to claim.
 */
export function PendingClaimCards({ claims, currentPlayerId, onDismiss }: PendingClaimCardsProps) {
  const t = useTranslate();
  // A claim that just landed reads "just claimed" until it is a minute old, so the
  // wording follows a clock the component owns rather than one read during render.
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    const tick = () => setNow(Date.now());
    tick();
    const id = setInterval(tick, 30_000);
    return () => clearInterval(id);
  }, []);

  if (claims.length === 0) return null;

  return (
    <View className="gap-2">
      <Card className="gap-1 border-bp-gold40 bg-bp-gold10">
        <View className="flex-row items-start justify-between gap-2">
          <View className="flex-1 gap-1">
            <Text className="text-center text-sm font-bold text-amber-500">
              {claims.length > 1 ? t('game.multipleClaimed') : t('game.singleClaimed')}
            </Text>
            <Text className="text-center text-xs text-bp-textSecondary">{t('game.claimedCheck')}</Text>
          </View>
          {/* The claim keeps the game paused whether or not anyone is looking at it,
              so the panel itself can be put away and brought back by the next claim. */}
          <Pressable
            onPress={onDismiss}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel={t('game.closeWindow') ?? 'Close window'}
            className="h-6 w-6 items-center justify-center rounded-full border border-bp-borderInactive bg-bp-surfaceAlt active:opacity-70"
          >
            <Text className="text-[11px] leading-none text-bp-textSecondary">✕</Text>
          </Pressable>
        </View>
      </Card>

      {claims.map((claim) => {
        const mine = currentPlayerId != null && claim.playerId === currentPlayerId;
        const cardId = claim.cardId ?? null;
        const time = claimedTime(claim.claimedAt, now);
        return (
          <Card key={claim.claimId} className={`gap-2 ${mine ? 'border-bp-gold50' : ''}`}>
            <View className="flex-row items-center justify-between gap-2">
              <Text className="text-bp-textPrimary text-sm font-bold" numberOfLines={1}>
                {claim.playerName}
                {cardId != null ? ` · ${t('game.cardNumber', { id: String(cardId) })}` : ''}
              </Text>
              <Text className="text-bp-textSecondary text-[10px]">
                {time == null ? t('game.justClaimed') : t('game.claimedAt', { time })}
              </Text>
            </View>

            {claim.cardNumbers.length > 0 ? (
              <CardGrid
                numbers={claim.cardNumbers}
                called={claim.calledNumbers}
                // The number that completed the card is lit separately from the rest
                // of the calls, so the claim can be checked against that one call.
                lastCalledNumber={
                  claim.calledNumbers.length > 0
                    ? claim.calledNumbers[claim.calledNumbers.length - 1]
                    : null
                }
              />
            ) : (
              <Text className="text-center text-xs text-bp-textSecondary">{t('common.notAvailable')}</Text>
            )}

            {claim.calledNumbers.length > 0 && (
              <Text className="text-center text-xs text-bp-textSecondary">
                {t('admin.claimCalledCount', { count: claim.calledNumbers.length })}
              </Text>
            )}

            {mine && (
              <Text className="text-center text-xs font-semibold text-bp-goldInk">
                {t('game.claimPending', { cardId: cardId != null ? String(cardId) : '' })}
              </Text>
            )}
          </Card>
        );
      })}
    </View>
  );
}