import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, AppState, RefreshControl, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { Button, Card, Screen } from '@/components/ui';
import { CardGrid } from '@/components/games/CardGrid';
import { NumberBoard } from '@/components/games/NumberBoard';
import { useTranslate } from '@/hooks/useTranslate';
import { useGameWebSocket } from '@/hooks/useGameWebSocket';
import { useGameStore } from '@/store/game.store';
import { AdminGameStateResponse, BingoClaimResponse, GameStatus, PendingClaimCard } from '@/types';

const round2 = (value: number) => Math.round(value * 100) / 100;
const money = (value: number) => value.toFixed(2);

export default function AdminLiveGameScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const gameId = Number(id);
  const t = useTranslate();

  const [state, setState] = useState<AdminGameStateResponse | null>(null);
  const [claims, setClaims] = useState<BingoClaimResponse[]>([]);
  const [claimCards, setClaimCards] = useState<PendingClaimCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);

  useGameWebSocket(gameId);

  // The game socket is unreliable (it rarely holds a session open), so this
  // screen treats polling as the source of truth and only uses the socket
  // store as an extra source of already-known numbers.
  const liveCalledCount = useGameStore((s) => s.calledNumbers.length);
  const liveCalledNumbers = useGameStore((s) => s.calledNumbers);
  // A claim announced on the socket has no id in the store, only the game
  // status, so its arrival is what tells us to pull the pending cards.
  const claimPendingFromSocket = useGameStore((s) => s.claimPending);

  const load = useCallback(async () => {
    try {
      const [stateRes, claimsRes, cardsRes] = await Promise.all([
        gamesApi.getAdminState(gameId),
        gamesApi.getPendingClaims(gameId),
        gamesApi.getPendingClaimCards(gameId).catch(() => null),
      ]);
      setState(stateRes.data);
      setClaims(claimsRes.data);
      setClaimCards(cardsRes?.data ?? []);
      setLoadError(null);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }, [gameId]);

  useEffect(() => {
    const id = setTimeout(() => void load(), 0);
    return () => clearTimeout(id);
  }, [load]);

  // Refetch as soon as a number lands on the socket.
  useEffect(() => {
    if (liveCalledCount === 0) return;
    const id = setTimeout(() => void load(), 0);
    return () => clearTimeout(id);
  }, [load, liveCalledCount]);

  // A claim arriving on the socket must show the card without waiting for the
  // next poll tick, otherwise the board looks like nothing was claimed.
  useEffect(() => {
    if (claimPendingFromSocket == null) return;
    const id = setTimeout(() => void load(), 0);
    return () => clearTimeout(id);
  }, [load, claimPendingFromSocket]);

  // Poll faster than the call interval so every number shows up promptly.
  useEffect(() => {
    const id = setInterval(() => void load(), 3_000);
    return () => clearInterval(id);
  }, [load]);

  // Coming back from background must not leave a stale board behind.
  useEffect(() => {
    const sub = AppState.addEventListener('change', (status) => {
      if (status === 'active') void load();
    });
    return () => sub.remove();
  }, [load]);

  // Render whichever source is further ahead. The socket delivers each call
  // immediately, so the board stays live even if the poll is slow or fails.
  const boardCalledNumbers = useMemo(() => {
    const fromApi = state?.calledNumbers ?? [];
    const fromSocket = liveCalledNumbers.map((c) => c.number);
    return fromSocket.length > fromApi.length ? fromSocket : fromApi;
  }, [state?.calledNumbers, liveCalledNumbers]);

  const pool = state?.prizePool ?? 0;
  const prize = state?.prizeAmount ?? null;
  const awaiting = claims.length;

  // What each winner actually walks away with. A player can only ever hold one
  // winning card, so the number of shares is the number of distinct claimants.
  const split = useMemo(() => {
    if (!state || claims.length === 0) return null;
    const winners = new Set(claims.map((c) => c.playerId)).size;
    if (winners === 0) return null;
    // The admin committed to a prize; their cut is whatever the pot has left.
    const totalPrize = prize ?? 0;
    const commission = round2(pool - totalPrize);
    return {
      winners,
      pot: money(pool),
      commission: money(commission),
      prize: money(totalPrize),
      // display only: the server settles the split to the cent when the last
      // claim is decided, and earlier winners absorb the odd cent
      share: money(round2(totalPrize / winners)),
    };
  }, [state, claims, pool, prize]);

  const run = async (fn: () => Promise<unknown>) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    try {
      await fn();
      await load();
    } catch (e) {
      Alert.alert((e as { userMessage?: string }).userMessage ?? 'Action failed');
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  return (
    <Screen>
      <View className="pb-1">
        <Text className="text-[11px] font-medium uppercase tracking-[0.2em] text-bp-textInactive">
          {t('admin.adgEyebrow') ?? 'Live game'}
        </Text>
        <Text className="mt-1 text-2xl font-bold text-bp-textPrimary">
          {t('admin.adgTitle', { id: String(gameId) }) ?? `Game #${gameId}`}
        </Text>
      </View>

      <ScrollView
        refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor="#6B5BFF" />}
        contentContainerClassName="gap-4 pb-8"
      >
        <Card className="flex-row justify-between">
          <View>
            <Text className="text-bp-textSecondary text-xs">{t('admin.status') ?? 'Status'}</Text>
            <Text className="text-bp-textPrimary font-bold">{state?.status ?? '—'}</Text>
          </View>
        </Card>

        {loadError != null && (
          <Card className="border-bp-danger50 bg-bp-danger15">
            <Text className="text-center text-[10px] font-bold text-red-500">
              {t('admin.liveSyncError') ?? 'Live update failed'} — {loadError}
            </Text>
          </Card>
        )}

        <NumberBoard
          calledNumbers={boardCalledNumbers}
          lastCalledNumber={boardCalledNumbers.length > 0 ? boardCalledNumbers[boardCalledNumbers.length - 1] : null}
        />

        <Card>
          <Text className="text-bp-textSecondary text-xs">{t('admin.prizePoolLabel') ?? 'Collected'}</Text>
          <Text className="text-bp-textPrimary font-bold text-2xl">{pool}</Text>
          {split && (
            <Text className="text-bp-textSecondary text-xs">
              {t('admin.prizeAndCut', { prize: split.prize, commission: split.commission }) ??
                `Prize ${split.prize} · your cut ${split.commission}`}
            </Text>
          )}
          <Text className="text-bp-textSecondary text-xs">
            {t('admin.pendingClaimsToReview', { count: awaiting })}
          </Text>
        </Card>

        <View className="flex-row gap-3">
          {state?.status === GameStatus.PAUSED && (
            <Button variant="outline" disabled={busy} onPress={() => void run(() => gamesApi.resume(gameId))}>
              {t('admin.resume') ?? '▶ Resume'}
            </Button>
          )}
          {(state?.status === GameStatus.IN_PROGRESS || state?.status === GameStatus.PAUSED) && (
            <Button variant="outline" disabled={busy} onPress={() => void run(() => gamesApi.pause(gameId))}>
              {t('admin.pause') ?? '⏸ Pause'}
            </Button>
          )}
          {state?.status !== GameStatus.ENDED && (
            <Button variant="danger" disabled={busy} onPress={() => void run(() => gamesApi.end(gameId))}>
              {t('game.endGame') ?? 'End game'}
            </Button>
          )}
        </View>

        <Text className="text-bp-textSecondary text-xs uppercase tracking-wider">
          {t('admin.pendingClaimsTitle', { count: awaiting }) ?? `Pending claims (${awaiting})`}
        </Text>

        {state?.status === GameStatus.CLAIM_PENDING && awaiting > 1 && (
          <Card className="border-bp-secondary">
            <Text className="text-bp-secondaryInk text-sm">
              {t('admin.tooManyWinnersHint', { max: 3 }) ?? 'Multiple winners may require a restart.'}
            </Text>
          </Card>
        )}

        {/* The split every confirmed winner gets is only final once the last claim
            is decided, so show the admin the exact arithmetic up front. */}
        {claims.length > 0 && split && (
          <Card className="border-bp-success gap-1">
            <Text className="text-bp-textPrimary font-semibold">
              {t('admin.equalSplitTitle', { count: split.winners }) ??
                `Equal split: ${split.winners} winner${split.winners > 1 ? 's' : ''}`}
            </Text>
            <Text className="text-bp-textSecondary text-sm">
              {t('admin.equalSplitMath', {
                pot: split.pot,
                commission: split.commission,
                prize: split.prize,
                share: split.share,
              }) ??
                `Prize ${split.prize} from ${split.pot} collected, you keep ${split.commission} → ${split.share} each`}
            </Text>
            {split.winners > 3 && (
              <Text className="text-bp-danger text-xs">
                {t('admin.equalSplitCapWarning', { max: 3 }) ??
                  `More than ${3} claims: approving restarts the round with a fresh number sequence.`}
              </Text>
            )}
          </Card>
        )}

        {claims.length === 0 ? (
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {t('admin.noPendingClaims', { status: state?.status ?? '—' }) ?? 'No pending claims.'}
            </Text>
          </Card>
        ) : (
          claims.map((claim) => {
            const card = claimCards.find((c) => c.claimId === claim.id) ?? null;
            return (
              <Card key={claim.id} className="gap-2">
                <Text className="text-bp-textPrimary font-semibold">
                  {t('admin.claimId', { id: String(claim.id) }) ?? `ID ${claim.id}`} ·{' '}
                  {card?.playerName ?? `#${claim.playerId}`}
                </Text>
                <Text className="text-bp-textSecondary text-sm">{claim.result}</Text>
                {/* The card itself, so the claim can actually be checked
                    against the called numbers instead of taken on trust. */}
                {card && (
                  <View className="gap-1">
                    <CardGrid
                      numbers={card.cardNumbers}
                      called={card.calledNumbers}
                      // Same as the player sees it: the call that completed the card is
                      // lit apart from the rest, so the review is against that number.
                      lastCalledNumber={
                        card.calledNumbers.length > 0
                          ? card.calledNumbers[card.calledNumbers.length - 1]
                          : null
                      }
                    />
                    {card.calledNumbers.length > 0 && (
                      <Text className="text-bp-textSecondary text-xs">
                        {t('admin.claimCalledCount', { count: card.calledNumbers.length }) ??
                          `${card.calledNumbers.length} numbers on this card have been called`}
                      </Text>
                    )}
                  </View>
                )}
                {/* A claim the card list could not resolve is called out rather than
                    left blank, so approving is never done on an invisible card. */}
                {!card && (
                  <Text className="text-bp-textSecondary text-xs">{t('common.notAvailable')}</Text>
                )}
                {split && (
                  <Text className="text-bp-successInk text-xs">
                    {t('admin.claimShare', { share: split.share }) ??
                      `If confirmed: ${split.share} share of the prize`}
                  </Text>
                )}
                <View className="flex-row gap-2">
                  <Button
                    variant="success"
                    disabled={busy}
                    onPress={() => void run(() => gamesApi.approveClaim(gameId, claim.id))}
                  >
                    {t('admin.approveClaim', {}) ?? 'Approve this claim'}
                  </Button>
                  <Button
                    variant="danger"
                    disabled={busy}
                    onPress={() =>
                      void run(() => gamesApi.rejectClaim(gameId, claim.id, t('admin.wdPresetUnclear') ?? 'Rejected'))
                    }
                  >
                    {t('admin.reject') ?? 'Reject'}
                  </Button>
                </View>
              </Card>
            );
          })
        )}

        <View className="flex-row gap-3">
          <Button variant="outline" disabled={busy} onPress={() => void run(() => gamesApi.restartGame(gameId))}>
            {t('admin.restartGame') ?? '↻ Restart game'}
          </Button>
          <Button variant="outline" disabled={busy} onPress={() => void run(() => gamesApi.approveAllClaims(gameId))}>
            {t('admin.approveAllShare', { count: awaiting }) ?? `Approve all (${awaiting})`}
          </Button>
        </View>
      </ScrollView>
    </Screen>
  );
}