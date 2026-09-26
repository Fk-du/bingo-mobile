import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { useTranslate } from '@/hooks/useTranslate';
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
        <Text className="text-bp-textSecondary text-center text-[10px]" numberOfLines={1}>
          {t('game.fairCommitment') ?? 'Fair play'}{' '}
          <Text className="text-bp-primary font-mono">
            {liveHash.slice(0, 12)}…{liveHash.slice(-6)}
          </Text>{' '}
          {t('game.fairCommitmentLive') ?? '— verified by you at the end'}
        </Text>
      </View>
    );
  }

  const tone =
    verdict.kind === 'verified'
      ? 'border-bp-accent40 bg-bp-accent10'
      : verdict.kind === 'failed'
        ? 'border-bp-danger50 bg-bp-danger10'
        : 'border-bp-borderInactive';

  return (
    <Card className={tone}>
      {verdict.kind === 'verifying' && (
        <Text className="text-bp-textSecondary text-xs">{t('game.checkingFairPlay') ?? 'Checking fair play…'}</Text>
      )}
      {verdict.kind === 'verified' && (
        <>
          <Text className="text-bp-accentInk text-sm font-bold">{t('game.verifiedFair') ?? 'Verified fair'}</Text>
          <Text className="text-bp-textSecondary text-[11px]">
            {t('game.verifiedFairDesc') ?? 'Your game was provably fair'}
          </Text>
          <Text className="text-bp-primary font-mono text-[10px] mt-1" numberOfLines={1}>
            {verdict.hash}
          </Text>
        </>
      )}
      {verdict.kind === 'failed' && (
        <>
          <Text className="text-bp-dangerInk text-sm font-bold">{t('game.fairnessFailed') ?? 'Fairness check failed'}</Text>
          <Text className="text-bp-textSecondary text-[11px]">{verdict.reason}</Text>
        </>
      )}
      {verdict.kind === 'unavailable' && (
        <Text className="text-bp-textSecondary text-xs">
          {t('game.noCommitmentRecorded') ?? 'No fairness commitment was recorded'}
        </Text>
      )}
    </Card>
  );
}