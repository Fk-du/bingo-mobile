import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, AppState, RefreshControl, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { Button, Card, Screen } from '@/components/ui';
import { NumberBoard } from '@/components/games/NumberBoard';
import { ResultsBoard } from '@/components/games/ResultsBoard';
import { useTranslate } from '@/hooks/useTranslate';
import { useGameWebSocket } from '@/hooks/useGameWebSocket';
import { useGameStore } from '@/store/game.store';
import { AdminGameStateResponse, GameStatus } from '@/types';

export default function AdminLiveGameScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const gameId = Number(id);
  const t = useTranslate();

  const [state, setState] = useState<AdminGameStateResponse | null>(null);
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
      const stateRes = await gamesApi.getAdminState(gameId);
      setState(stateRes.data);
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

  // A claim arriving on the socket no longer needs the board to react: the
  // engine auto-decides it.
  useEffect(() => {
    if (liveCalledCount === 0) return;
    const id = setTimeout(() => void load(), 0);
    return () => clearTimeout(id);
  }, [load, liveCalledCount]);

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
          {state?.prizeAmount != null && state.prizeAmount > 0 && (
            <Text className="text-bp-textSecondary text-xs">
              {t('game.prizeAmount', {}) ?? `Prize ${state.prizeAmount}`}
            </Text>
          )}
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

        {/* Read-only results. Every claim was decided by the engine from the
            server truth, so the admin has nothing to approve — the board is the
            same one the room sees. */}
        <Text className="text-bp-textSecondary text-xs uppercase tracking-wider">
          {t('admin.resultsTitle') ?? 'Results'}
        </Text>
        {state?.status === GameStatus.ENDED ? (
          <ResultsBoard
            winnerCards={state.winnerCards ?? []}
            bannedCards={state.bannedCards ?? []}
            calledNumbers={boardCalledNumbers}
          />
        ) : (
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {t('admin.resultsAfterEnd') ?? 'The winners and banned cards appear here when the game ends.'}
            </Text>
          </Card>
        )}

        {state?.status === GameStatus.ENDED && (
          <Button variant="outline" disabled={busy} onPress={() => void run(() => gamesApi.restartGame(gameId))}>
            {t('admin.restartGame') ?? '↻ Restart game'}
          </Button>
        )}
      </ScrollView>
    </Screen>
  );
}