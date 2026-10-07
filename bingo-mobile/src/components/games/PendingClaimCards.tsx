import { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Card } from '@/components/ui';
import { CardGrid } from '@/components/games/CardGrid';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
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
  const { colors } = useTheme();
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
      <Card className="gap-1" style={{ borderColor: '#8B5E3C40', backgroundColor: '#8B5E3C10' }}>
        <View className="flex-row items-start justify-between gap-2">
          <View className="flex-1 gap-1">
            <Text className="text-center text-sm font-bold" style={{ color: '#8B5E3C' }}>
              {claims.length > 1 ? t('game.multipleClaimed') : t('game.singleClaimed')}
            </Text>
            <Text className="text-center text-xs" style={{ color: colors.textSecondary }}>{t('game.claimedCheck')}</Text>
          </View>
          <Pressable
            onPress={onDismiss}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel={t('game.closeWindow') ?? 'Close window'}
            className="h-6 w-6 items-center justify-center rounded-full border active:opacity-70"
            style={{ borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt }}
          >
            <Text className="text-[11px] leading-none" style={{ color: colors.textSecondary }}>✕</Text>
          </Pressable>
        </View>
      </Card>

      {claims.map((claim) => {
        const mine = currentPlayerId != null && claim.playerId === currentPlayerId;
        const cardId = claim.cardId ?? null;
        const time = claimedTime(claim.claimedAt, now);
        return (
          <Card key={claim.claimId} className="gap-2" style={mine ? { borderColor: '#8B5E3C50' } : undefined}>
            <View className="flex-row items-center justify-between gap-2">
              <Text className="text-sm font-bold" style={{ color: colors.textPrimary }} numberOfLines={1}>
                {claim.playerName}
                {cardId != null ? ` · ${t('game.cardNumber', { id: String(cardId) })}` : ''}
              </Text>
              <Text className="text-[10px]" style={{ color: colors.textSecondary }}>
                {time == null ? t('game.justClaimed') : t('game.claimedAt', { time })}
              </Text>
            </View>

            {claim.cardNumbers.length > 0 ? (
              <CardGrid
                numbers={claim.cardNumbers}
                called={claim.calledNumbers}
                lastCalledNumber={
                  claim.calledNumbers.length > 0
                    ? claim.calledNumbers[claim.calledNumbers.length - 1]
                    : null
                }
              />
            ) : (
              <Text className="text-center text-xs" style={{ color: colors.textSecondary }}>{t('common.notAvailable')}</Text>
            )}

            {claim.calledNumbers.length > 0 && (
              <Text className="text-center text-xs" style={{ color: colors.textSecondary }}>
                {t('admin.claimCalledCount', { count: claim.calledNumbers.length })}
              </Text>
            )}

            {mine && (
              <Text className="text-center text-xs font-semibold" style={{ color: colors.gold }}>
                {t('game.claimPending', { cardId: cardId != null ? String(cardId) : '' })}
              </Text>
            )}
          </Card>
        );
      })}
    </View>
  );
}