import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { sha256Hex } from '@/lib/sha256';
import { GameStatus } from '@/types';
import { Card } from '@/components/ui';

type Verdict =
  | { kind: 'verifying' }
  | { kind: 'verified'; hash: string }
  | { kind: 'failed'; reason: string }
  | { kind: 'unavailable' };

export function FairnessPanel({
  gameId,
  status,
  liveHash,
}: {
  gameId: number;
  status?: GameStatus | null;
  liveHash?: string | null;
}) {
  const t = useTranslate();
  const { colors } = useTheme();
  const over = status === GameStatus.ENDED;
  const [verdict, setVerdict] = useState<Verdict>({ kind: 'verifying' });

  useEffect(() => {
    if (!over) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await gamesApi.getFairness(gameId);
        if (cancelled) return;
        const p = res.data;
        if (!p.fairnessHash || !p.sequence) {
          setVerdict({ kind: 'unavailable' });
          return;
        }
        const recomputed = await sha256Hex(p.sequence.join(','));
        if (cancelled) return;
        if (!p.sequenceIntact || recomputed !== p.fairnessHash) {
          setVerdict({ kind: 'failed', reason: t('game.fairnessFailedCallOrder') ?? 'Call order was tampered with' });
          return;
        }
        setVerdict({ kind: 'verified', hash: recomputed });
      } catch {
        if (!cancelled) {
          setVerdict({ kind: 'failed', reason: t('game.fairnessFailedBrowser') ?? 'Could not verify in this app' });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [over, gameId, t]);

  if (!over) {
    if (!liveHash) return null;
    return (
      <View className="px-1 py-2">
        <Text className="text-center text-[10px]" numberOfLines={1} style={{ color: colors.textSecondary }}>
          {t('game.fairCommitment') ?? 'Fair play'}{' '}
          <Text className="font-mono" style={{ color: colors.primary }}>
            {liveHash.slice(0, 12)}…{liveHash.slice(-6)}
          </Text>{' '}
          {t('game.fairCommitmentLive') ?? '— verified by you at the end'}
        </Text>
      </View>
    );
  }

  const toneStyle =
    verdict.kind === 'verified'
      ? { borderColor: '#36E4B440', backgroundColor: '#36E4B510' }
      : verdict.kind === 'failed'
        ? { borderColor: '#FF5C6C50', backgroundColor: '#FF5C6C10' }
        : { borderColor: colors.borderInactive, backgroundColor: colors.surface };

  return (
    <Card style={toneStyle}>
      {verdict.kind === 'verifying' && (
        <Text className="text-xs" style={{ color: colors.textSecondary }}>{t('game.checkingFairPlay') ?? 'Checking fair play…'}</Text>
      )}
      {verdict.kind === 'verified' && (
        <>
          <Text className="text-sm font-bold" style={{ color: colors.accent }}>{t('game.verifiedFair') ?? 'Verified fair'}</Text>
          <Text className="text-[11px]" style={{ color: colors.textSecondary }}>
            {t('game.verifiedFairDesc') ?? 'Your game was provably fair'}
          </Text>
          <Text className="font-mono text-[10px] mt-1" style={{ color: colors.primary }} numberOfLines={1}>
            {verdict.hash}
          </Text>
        </>
      )}
      {verdict.kind === 'failed' && (
        <>
          <Text className="text-sm font-bold" style={{ color: colors.danger }}>{t('game.fairnessFailed') ?? 'Fairness check failed'}</Text>
          <Text className="text-[11px]" style={{ color: colors.textSecondary }}>{verdict.reason}</Text>
        </>
      )}
      {verdict.kind === 'unavailable' && (
        <Text className="text-xs" style={{ color: colors.textSecondary }}>
          {t('game.noCommitmentRecorded') ?? 'No fairness commitment was recorded'}
        </Text>
      )}
    </Card>
  );
}