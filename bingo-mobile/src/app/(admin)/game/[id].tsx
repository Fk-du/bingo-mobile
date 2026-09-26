import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, AppState, RefreshControl, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { Button, Card, Screen, Title } from '@/components/ui';
import { NumberBoard } from '@/components/games/NumberBoard';
import { useTranslate } from '@/hooks/useTranslate';
import { useGameWebSocket } from '@/hooks/useGameWebSocket';
import { useGameStore } from '@/store/game.store';
import { AdminGameStateResponse, BingoClaimResponse, GameStatus } from '@/types';

export default function AdminLiveGameScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const gameId = Number(id);
  const t = useTranslate();

  const [state, setState] = useState<AdminGameStateResponse | null>(null);
  const [claims, setClaims] = useState<BingoClaimResponse[]>([]);
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

  const load = useCallback(async () => {
    try {
      const [stateRes, claimsRes] = await Promise.all([
        gamesApi.getAdminState(gameId),
        gamesApi.getPendingClaims(gameId),
      ]);
      setState(stateRes.data);
      setClaims(claimsRes.data);
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

  const pool = state?.prizePool ?? 0;
  const awaiting = claims.length;

  return (
    <Screen>
      <Title className="text-xl">
        {t('admin.adgTitle', { id: String(gameId) }) ?? `Game #${gameId}`}
      </Title>

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
          <Text className="text-bp-textSecondary text-xs">{t('admin.prizePoolLabel') ?? 'Prize pool'}</Text>
          <Text className="text-bp-textPrimary font-bold text-2xl">{pool}</Text>
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

        {claims.length === 0 ? (
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {t('admin.noPendingClaims', { status: state?.status ?? '—' }) ?? 'No pending claims.'}
            </Text>
          </Card>
        ) : (
          claims.map((claim) => (
            <Card key={claim.id} className="gap-2">
              <Text className="text-bp-textPrimary font-semibold">
                {t('admin.claimId', { id: String(claim.id) }) ?? `ID ${claim.id}`} ·{' '}
                #{claim.playerId}
              </Text>
              <Text className="text-bp-textSecondary text-sm">{claim.result}</Text>
              <View className="flex-row gap-2">
                <Button
                  variant="danger"
                  disabled={busy}
                  onPress={() =>
                    void run(() => gamesApi.rejectClaim(gameId, claim.id, t('admin.wdPresetUnclear') ?? 'Rejected'))
                  }
                >
                  {t('admin.reject') ?? 'Reject'}
                </Button>
                <Button variant="outline" disabled={busy} onPress={() => void run(() => gamesApi.approveAllClaims(gameId))}>
                  {t('admin.approveWinnerEnd', {}) ?? 'Approve & pay'}
                </Button>
              </View>
            </Card>
          ))
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