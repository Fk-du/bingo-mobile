import { useLocalSearchParams } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { CardGrid } from '@/components/games/CardGrid';
import { FairnessPanel } from '@/components/games/FairnessPanel';
import { StartCountdownBanner } from '@/components/games/StartCountdownBanner';
import { WinnerModal } from '@/components/games/WinnerModal';
import { NumberBoard } from '@/components/games/NumberBoard';
import { CardPickerModal } from '@/components/games/CardPickerModal';
import { useNumberAnnouncer } from '@/hooks/useNumberAnnouncer';
import { Button, Card, Screen, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useCountdown } from '@/hooks/useCountdown';
import { useGameWebSocket } from '@/hooks/useGameWebSocket';
import { patternProgress, patternCells, customCellsFromJson, computeProgress } from '@/lib/pattern';
import { useGameStore } from '@/store/game.store';
import { PlayerGameResponse, GameStateResponse, GameStatus, PlayerCardView } from '@/types';

export default function LiveGameScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const gameId = Number(id);
  const t = useTranslate();
  const qc = useQueryClient();

  // Subscribe per-field. Selecting the whole store re-renders this entire
  // screen (every CardGrid) on any store write, including the ones the
  // socket makes from outside React.
  const gameStatus = useGameStore((s) => s.gameStatus);
  const calledNumberEntries = useGameStore((s) => s.calledNumbers);
  const playerCards = useGameStore((s) => s.playerCards);
  const claimPending = useGameStore((s) => s.claimPending);
  const restartNotice = useGameStore((s) => s.restartNotice);
  const totalNumbersCalled = useGameStore((s) => s.totalNumbersCalled);
  const prizeAmount = useGameStore((s) => s.prizeAmount);
  const isConnecting = useGameStore((s) => s.isConnecting);
  const startTime = useGameStore((s) => s.startTime);
  const startReason = useGameStore((s) => s.startReason);
  const setPlayerCards = useGameStore((s) => s.setPlayerCards);
  const setRestartNotice = useGameStore((s) => s.setRestartNotice);

  const game = useMemo(
    () => ({
      gameStatus,
      calledNumbers: calledNumberEntries,
      playerCards,
      claimPending,
      restartNotice,
      totalNumbersCalled,
      prizeAmount,
      setPlayerCards,
      setRestartNotice,
    }),
    [
      gameStatus,
      calledNumberEntries,
      playerCards,
      claimPending,
      restartNotice,
      totalNumbersCalled,
      prizeAmount,
      setPlayerCards,
      setRestartNotice,
    ]
  );
  const [state, setState] = useState<GameStateResponse | null>(null);
  const [meta, setMeta] = useState<PlayerGameResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [claiming, setClaiming] = useState(false);
  const [report, setReport] = useState<{ message: string; kind: 'win' | 'pending' | 'banned' | 'error' } | null>(null);
  const [globalMarks, setGlobalMarks] = useState<Set<number>>(new Set());
  const [pickerOpen, setPickerOpen] = useState(false);
  const [registerSuccess, setRegisterSuccess] = useState(false);
  const marksSeeded = useRef(false);

  useGameWebSocket(gameId);

  const loadState = useCallback(async () => {
    try {
      const [res, activeRes] = await Promise.all([
        gamesApi.getState(gameId),
        gamesApi.getActive().catch(() => null),
      ]);
      setState(res.data);
      const metaGame = activeRes?.data?.find((g) => g.id === gameId) ?? null;
      if (metaGame) setMeta(metaGame);
      const store = useGameStore.getState();
      store.setActiveGame(gameId);
      store.setGameStatus(res.data.status);
      store.setStartTime(res.data.startTime ?? null);
      if (res.data.status === GameStatus.STARTING) {
        // The poll carries no reason: a game that already called numbers is a
        // resume, and a socket that announced one keeps its own wording.
        store.setStartReason(
          store.startReason ?? (res.data.totalNumbersCalled > 0 ? 'resume' : 'start')
        );
      } else {
        store.setStartReason(null);
      }
      store.setCalledNumbers(
        res.data.calledNumbers.map((n, i) => ({ id: i, gameId, number: n, sequenceIndex: i, calledAt: null }))
      );
      store.setPrizeAmount(res.data.prizeAmount ?? null);
      store.setTotalNumbersCalled(res.data.totalNumbersCalled);
      store.setPlayerCards(res.data.playerCards);
      if (res.data.playerCards?.length && !marksSeeded.current) {
        marksSeeded.current = true;
        const seed = new Set<number>();
        for (const pc of res.data.playerCards) {
          for (const n of pc.markedNumbers ?? []) seed.add(n);
        }
        if (res.data.autoMark === false) seed.add(0);
        setGlobalMarks(seed);
      }
      if (res.data.status !== GameStatus.REGISTRATION_OPEN) setRegisterSuccess(false);
    } finally {
      setLoading(false);
    }
  }, [gameId]);

  useEffect(() => {
    void loadState();
  }, [loadState]);

  // The socket is the fast path, but it is not the only way this screen learns
  // a number was called: a dropped session, a proxy that drops the upgrade or a
  // phone that slept through it all leave the board frozen until the player
  // pulls to refresh. Polling keeps the board moving on its own, and the
  // refetch on every socket event keeps it instant when the socket is healthy.
  useEffect(() => {
    const id = setInterval(() => void loadState(), 4_000);
    return () => clearInterval(id);
  }, [loadState]);

  // When the socket is healthy it delivers a number the instant it is called;
  // pull the rest of the state (cards, pool, marks) straight away rather than
  // waiting for the next poll tick.
  const liveCalledCount = calledNumberEntries.length;
  useEffect(() => {
    if (liveCalledCount === 0) return;
    const id = setTimeout(() => void loadState(), 0);
    return () => clearTimeout(id);
  }, [loadState, liveCalledCount]);

  // Coming back from background must not leave a stale board behind.
  useEffect(() => {
    const sub = AppState.addEventListener('change', (status) => {
      if (status === 'active') void loadState();
    });
    return () => sub.remove();
  }, [loadState]);

  // Countdown and winner result
  const isCountingDown = gameStatus === GameStatus.STARTING;
  const countdownSeconds = useCountdown(startTime, isCountingDown);
  const winningCard = useMemo(
    () => playerCards?.find((c) => c.winner) ?? null,
    [playerCards]
  );
  const [winnerModalSeen, setWinnerModalSeen] = useState(false);
  const showWinnerModal = gameStatus === GameStatus.ENDED && Boolean(state?.isWinner) && !winnerModalSeen;

  const isManual = state?.autoMark === false;
  const lastCalledNumber =
    game.calledNumbers.length > 0 ? game.calledNumbers[game.calledNumbers.length - 1].number : null;

  const toggleMark = async (n: number) => {
    if (!isManual) return;
    const next = new Set(globalMarks);
    if (next.has(0)) next.delete(0);
    if (next.has(n)) next.delete(n);
    else next.add(n);
    setGlobalMarks(next);
    const cards = game.playerCards ?? [];
    game.setPlayerCards(
      cards.map((c) => ({ ...c, markedNumbers: [...next] }))
    );
    try {
      await Promise.all(
        cards.map((c) => gamesApi.saveMarks(gameId, c.cardId, [...next], false))
      );
    } catch {
      // optimistic update; ignore failure for UI continuity
    }
  };

  const claimCard = async (card: PlayerCardView) => {
    if (claiming) return;
    setClaiming(true);
    setReport(null);
    try {
      const res = await gamesApi.claim(gameId, card.cardId, isManual ? [...globalMarks] : undefined, !isManual);
      if (res.data.restarted) {
        marksSeeded.current = false;
        setGlobalMarks(new Set());
        game.setRestartNotice(t('mobile.restartNotice') ?? 'Game restarting…');
        setReport({ message: t('mobile.restartNotice') ?? 'Game restarting…', kind: 'pending' });
      } else if (res.data.banned) {
        setReport({ message: t('game.claimBanned', { cardId: String(card.cardId) }) ?? 'This card is banned', kind: 'banned' });
      } else if (res.data.pendingReview) {
        setReport({
          message: t('game.claimPending', { cardId: String(card.cardId) }) ?? 'Claim pending review…',
          kind: 'pending',
        });
      } else if (res.data.valid) {
        setReport({
          message: t('game.claimWin', { amount: (res.data.rewardAmount ?? 0).toLocaleString() }) ?? 'You won!',
          kind: 'win',
        });
      }
      await loadState();
    } catch (e) {
      const msg = (e as { userMessage?: string }).userMessage;
      if (msg) setReport({ message: msg, kind: 'error' });
      await loadState();
    } finally {
      setClaiming(false);
    }
  };

  const calledNumbers = game.calledNumbers.map((c) => c.number);
  const { muted, toggleMuted } = useNumberAnnouncer(calledNumbers);
  const prize = state?.prizeAmount ?? game.prizeAmount;
  const winningPattern = state?.winningPattern ?? null;
  const patternCellsSet = winningPattern
    ? winningPattern === 'CUSTOM'
      ? customCellsFromJson(state?.customPatternCells)
      : patternCells(winningPattern)
    : null;
  const patternLabel =
    winningPattern === 'CUSTOM'
      ? state?.customPatternName || (t('patterns.CUSTOM') ?? 'Custom')
      : winningPattern
        ? (t(`patterns.${winningPattern}`) ?? winningPattern)
        : null;
  const patternHint = winningPattern
    ? winningPattern === 'CUSTOM'
      ? (t('patterns.hintCUSTOM') ?? null)
      : (t(`patterns.hint${winningPattern}`) ?? null)
    : null;

  const hasCards = (game.playerCards?.length ?? 0) > 0;
  const isLive = game.gameStatus === GameStatus.IN_PROGRESS;
  const isRegistration = game.gameStatus === GameStatus.REGISTRATION_OPEN;
  const entryFee = meta?.entryFee;

  return (
    <Screen>
      <View className="flex-row items-center justify-between py-1">
        <View className="min-w-0 flex-1">
          <Title className="text-xl">
            {t('game.gameNumber', { id: String(gameId) }) ?? `Game #${gameId}`}
          </Title>
          <Text className="text-xs text-bp-textSecondary">
            {t('game.maxPlayers', { max: String(meta?.maxPlayers ?? '—') }) ?? `Max ${meta?.maxPlayers ?? '—'} players`}
          </Text>
        </View>
        {isLive && (
          <View className="flex-row items-center gap-1.5 rounded-full border border-bp-danger40 bg-bp-danger10 px-3 py-1">
            <View className="h-2 w-2 rounded-full bg-bp-danger" />
            <Text className="text-[10px] font-bold uppercase tracking-wider text-red-500">
              {t('game.live') ?? 'Live'}
            </Text>
          </View>
        )}
        {/* While the socket is still connecting or retrying, say so: the board
            keeps updating through the poll, but the player should not sit there
            assuming the game is stalled. */}
        {isConnecting && (
          <View className="flex-row items-center gap-1.5 rounded-full border border-bp-border bg-bp-background px-3 py-1">
            <View className="h-2 w-2 rounded-full bg-bp-textSecondary" />
            <Text className="text-[10px] font-bold uppercase tracking-wider text-bp-textSecondary">
              {t('game.syncing') ?? 'Syncing'}
            </Text>
          </View>
        )}
      </View>

      <View className="border-b border-bp-borderInactive bg-bp-bg pt-2 pb-1.5">
        <NumberBoard calledNumbers={calledNumbers} lastCalledNumber={lastCalledNumber} />
      </View>

      <ScrollView
        style={{ flex: 1 }}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={loadState} tintColor="#6B5BFF" />}
        contentContainerClassName="gap-3 pb-8"
      >
        {winningPattern && patternLabel && (
          <View className="mt-1 gap-2">
            <View className="rounded-2xl border border-bp-gold25 bg-bp-surface p-3">
              <View className="mb-2 flex-row items-center justify-between">
                <Text className="text-[10px] font-bold uppercase tracking-[0.2em] text-bp-textInactive">
                  {t('game.winningPattern') ?? 'Winning pattern'}
                </Text>
                {patternCellsSet?.size ? (
                  <View className="rounded-full border border-bp-gold30 bg-bp-gold10 px-2 py-0.5">
                    <Text className="text-[9px] font-bold text-bp-goldInk">
                      {patternCellsSet.size} {t('game.patternCells') ?? 'cells'}
                    </Text>
                  </View>
                ) : null}
              </View>
              <View className="flex-row items-center gap-3">
                <MiniPattern cells={patternCellsSet} />
                <View className="min-w-0 flex-1">
                  <Text className="text-base font-black text-bp-goldInk">{patternLabel}</Text>
                  {patternHint ? (
                    <Text className="mt-0.5 text-[11px] leading-snug text-bp-textSecondary">{patternHint}</Text>
                  ) : null}
                </View>
              </View>
            </View>
          </View>
        )}

        <Card className="items-center overflow-hidden py-4">
          <Text className="text-[10px] uppercase tracking-wider text-bp-textSecondary">
            {t('mobile.jackpotPrize') ?? 'Prize'}
          </Text>
          <Text className="mt-1 text-3xl font-black text-bp-goldInk">
            {(prize == null ? '—' : prize.toLocaleString())}
          </Text>
          <View className="mt-1 flex-row items-center gap-2">
            {state?.winnerCount && state.winnerCount > 1 ? (
              <Text className="text-[10px] text-bp-textSecondary">
                {t('game.prizeShared', { count: String(state.winnerCount) }) ??
                  `shared between ${state.winnerCount} winners`}
              </Text>
            ) : (
              <Text className="text-[10px] text-bp-textSecondary">{t('game.coins') ?? 'birr'}</Text>
            )}
            <Text className="text-[10px] font-bold text-bp-textSecondary">
              {t('game.called') ?? 'Called'}: {game.totalNumbersCalled}/75
            </Text>
          </View>
        </Card>

        <FairnessPanel gameId={gameId} status={game.gameStatus} liveHash={state?.fairnessHash} />

        <StartCountdownBanner status={game.gameStatus} reason={startReason} seconds={countdownSeconds} />

        {game.gameStatus === GameStatus.ENDED && (
          <Card
            className={
              state?.isWinner
                ? 'border-bp-success40 bg-bp-success10'
                : 'border-bp-borderInactive bg-bp-surface'
            }
          >
            <Text
              className={`text-center text-base font-bold ${
                state?.isWinner ? 'text-emerald-500' : 'text-bp-textSecondary'
              }`}
            >
              {state?.isWinner ? t('game.youWon') ?? '🎉 BINGO! You Won! 🎉' : (t('game.gameOver') ?? 'Game Over')}
            </Text>
          </Card>
        )}

        {game.restartNotice ? (
          <Card className="border-bp-gold40 bg-bp-gold10">
            <Text className="text-center text-sm font-bold text-amber-500">🔁 {game.restartNotice}</Text>
          </Card>
        ) : null}

        {game.claimPending ? (
          <Card className="border-bp-gold40 bg-bp-gold10">
            <Text className="text-center text-sm font-semibold text-amber-500">
              ⏳ {t('game.claimPending', { cardId: '' }) ?? 'Claim pending review…'}
            </Text>
          </Card>
        ) : null}

        {report && (
          <Card className={reportTone(report.kind)}>
            <Text className={`text-center text-sm font-semibold ${reportTextTone(report.kind)}`}>{report.message}</Text>
          </Card>
        )}

        {isRegistration && (
          <>
            {hasCards ? (
              <Card className="items-center py-4">
                <Text className="text-[10px] font-bold uppercase tracking-[0.25em] text-bp-textSecondary">
                  {t('game.youHold', { count: String(game.playerCards?.length ?? 0), fee: String(entryFee ?? '') }) ??
                    `You hold ${game.playerCards?.length ?? 0} cards`}
                </Text>
                <Button variant="primary" className="mt-3" onPress={() => setPickerOpen(true)}>
                  {t('game.buyAnotherCard') ?? '+ Buy Another Card'}
                </Button>
              </Card>
            ) : (
              <Card className="items-center py-6">
                <Text className="text-[10px] font-bold uppercase tracking-[0.25em] text-bp-textSecondary">
                  {t('game.registrationOpen') ?? 'Registration Open'}
                </Text>
                <Text className="mt-2 text-3xl font-black text-bp-goldInk">{entryFee ?? '?'}</Text>
                <Text className="text-sm text-bp-textSecondary">{t('game.coinsPerCard') ?? 'birr per card'}</Text>
                <Button className="mt-4" onPress={() => setPickerOpen(true)}>
                  {t('game.chooseCard') ?? '✦ Choose a Card'}
                </Button>
              </Card>
            )}
            {registerSuccess && (
              <Card className="border-bp-success40 bg-bp-success10 px-3 py-2">
                <Text className="text-center text-emerald-500 text-sm font-semibold">✓ {t('game.registeredReady') ?? 'Registered!'}</Text>
              </Card>
            )}
          </>
        )}

        {hasCards ? (
          <View className="flex-row flex-wrap" style={{ gap: 10 }}>
            {game.playerCards!.map((card) => {
              const marked = isManual ? globalMarks : new Set(card.markedNumbers ?? []);
              const prog = isManual ? patternProgress(card.numbers, marked, winningPattern) : null;
              const progCustom =
                isManual && winningPattern === 'CUSTOM'
                  ? computeProgress(card.numbers, marked, customCellsFromJson(state?.customPatternCells) ?? new Set())
                  : null;
              const done = prog?.done ?? progCustom?.done;
              const total = prog?.total ?? progCustom?.total;
              const patternDone = isManual && total != null && done === total;
              const claimable = isLive && !card.banned && !card.winner && !claiming;
              return (
                <View key={card.cardId} className="gap-1.5" style={{ width: '48%' }}>
                  <View className="flex-row items-center justify-between gap-1.5">
                    <Text className="rounded-full border border-bp-borderInactive bg-bp-surfaceAlt px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider text-bp-textSecondary">
                      {t('game.cardNumber', { id: String(card.cardId) }) ?? `Card #${card.cardId}`}
                    </Text>
                    <View className="flex-row gap-1">
                      {card.winner && (
                        <Text className="rounded-full border border-bp-success40 bg-bp-success10 px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider text-emerald-500">
                          {t('game.winnerBadge') ?? 'Winner'}
                        </Text>
                      )}
                      {card.banned && (
                        <Text className="rounded-full border border-bp-danger50 bg-bp-danger15 px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider text-red-500">
                          {t('game.bannedBadge') ?? 'Banned'}
                        </Text>
                      )}
                    </View>
                  </View>
                  <View className={card.banned ? 'opacity-60' : ''}>
                    <CardGrid
                      numbers={card.numbers}
                      called={calledNumbers}
                      marked={isManual ? [...marked] : []}
                      lastCalledNumber={lastCalledNumber}
                      interactive={isManual && !card.banned && !card.winner}
                      onToggle={(n) => void toggleMark(n)}
                    />
                  </View>
                  {card.banned && (
                    <Text className="text-center text-[10px] text-red-500/80">
                      {t('game.bannedCardHint') ?? 'This card is banned.'}
                    </Text>
                  )}
                  {isManual && total != null ? (
                    <View
                      className={`self-center rounded-full border px-2 py-0.5 ${
                        patternDone ? 'border-bp-gold40 bg-bp-gold10' : 'border-bp-borderInactive bg-bp-surfaceAlt'
                      }`}
                    >
                      <Text
                        className={`text-[9px] font-bold ${patternDone ? 'text-bp-goldInk' : 'text-bp-textSecondary'}`}
                      >
                        {patternDone
                          ? `✓ ${done}/${total}`
                          : `${t('game.patternProgress', { done: String(done), total: String(total) }) ?? `${done}/${total}`}`}
                      </Text>
                    </View>
                  ) : null}
                  {claimable ? (
                    <Pressable
                      onPress={() => void claimCard(card)}
                      style={{ boxShadow: '0 0 14px rgba(235,87,87,0.35)' }}
                      className="w-full items-center rounded-xl bg-bp-danger py-2.5 active:opacity-80"
                    >
                      <Text className="text-sm font-black tracking-[0.15em] text-white">
                        {claiming ? t('game.checking') ?? '✦ CHECKING…' : t('game.bingoBtn') ?? '✦ BINGO! ✦'}
                      </Text>
                    </Pressable>
                  ) : null}
                </View>
              );
            })}
          </View>
        ) : !isRegistration ? (
          <Card>
            <Text className="text-center text-sm text-bp-textSecondary">
              {t('game.noCardsAvailable') ?? 'No cards registered yet'}
            </Text>
          </Card>
        ) : null}

        {hasCards && (
          <>
            <View className="flex-row items-center">
              <AutoMarkToggle
                manual={isManual}
                onToggle={async () => {
                  const next = !isManual;
                  const cards = game.playerCards ?? [];
                  if (next) {
                    const ported = new Set<number>();
                    for (const pc of cards) {
                      for (const row of pc.numbers) {
                        for (const num of row) {
                          if (calledNumbers.includes(num)) ported.add(num);
                        }
                      }
                    }
                    ported.add(0);
                    setGlobalMarks(ported);
                    for (const pc of cards) {
                      await gamesApi.saveMarks(gameId, pc.cardId, [...ported], false);
                    }
                  } else {
                    setGlobalMarks(new Set());
                    for (const pc of cards) {
                      await gamesApi.saveMarks(gameId, pc.cardId, [], true);
                    }
                  }
                  await loadState();
                }}
                t={t}
              />
              <Pressable
                onPress={toggleMuted}
                className="ml-2 flex-row items-center gap-2 rounded-xl border border-bp-borderInactive bg-bp-surfaceAlt px-3 py-2"
              >
                <View
                  className={`h-5 w-9 rounded-full justify-center ${
                    muted ? 'bg-bp-textInactive' : 'bg-bp-primary'
                  }`}
                  style={{ paddingLeft: 2 }}
                >
                  <View
                    className={`h-4 w-4 rounded-full bg-white ${muted ? '' : 'self-end'}`}
                  />
                </View>
                <Text className="text-xs text-bp-textSecondary">
                  {t('game.sound') ?? 'Sound'}
                  {muted ? ` · ${t('game.soundOff') ?? 'Off'}` : ` · ${t('game.soundOn') ?? 'On'}`}
                </Text>
              </Pressable>
            </View>
            {isManual && (
              <Text className="text-center text-xs text-bp-textSecondary">
                {t('game.tapToMark') ?? 'Tap a number to mark it'}
              </Text>
            )}
          </>
        )}
      </ScrollView>

      {pickerOpen && (
        <CardPickerModal
          gameId={gameId}
          entryFee={entryFee ?? 0}
          onClose={() => setPickerOpen(false)}
          onRegistered={() => {
            setRegisterSuccess(true);
            // Registering from inside a game charges the entry fee too, so the
            // lobby balance card must not keep showing the pre-registration total.
            void qc.invalidateQueries({ queryKey: ['wallet'] });
          }}
        />
      )}
      <WinnerModal
        visible={showWinnerModal}
        card={winningCard}
        calledNumbers={calledNumbers}
        rewardAmount={state?.rewardAmount ?? null}
        winners={state?.winnerCount ?? null}
        onClose={() => setWinnerModalSeen(true)}
      />

    </Screen>
  );
}

function MiniPattern({ cells, size = 11 }: { cells: Set<string> | null; size?: number }) {
  const gap = 3;
  const pad = 6;
  return (
    <View
      className="rounded-xl border border-bp-gold20 bg-bp-bg"
      style={{ padding: pad, gap, width: size * 5 + gap * 4 + pad * 2 }}
    >
      {Array.from({ length: 5 }, (_, r) => (
        <View key={r} className="flex-row" style={{ gap }}>
          {Array.from({ length: 5 }, (_, c) => {
            const isFree = r === 2 && c === 2;
            const needed = cells?.has(`${r},${c}`);
            return (
              <View
                key={c}
                style={{
                  width: size,
                  height: size,
                  borderRadius: 2.5,
                  ...(isFree
                    ? { boxShadow: '0 0 5px rgba(242,201,76,0.65)' }
                    : needed
                      ? { boxShadow: '0 0 5px rgba(107,91,255,0.8)' }
                      : {}),
                }}
                className={isFree ? 'bg-bp-gold' : needed ? 'bg-bp-primary' : 'bg-bp-surface'}
              />
            );
          })}
        </View>
      ))}
    </View>
  );
}

function AutoMarkToggle({
  manual,
  onToggle,
  t,
}: {
  manual: boolean;
  onToggle: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  return (
    <Pressable onPress={() => void onToggle()} className="flex-row items-center gap-2 rounded-xl border border-bp-borderInactive bg-bp-surfaceAlt px-3 py-2">
      <View className={`h-5 w-9 rounded-full justify-center ${manual ? 'bg-bp-textInactive' : 'bg-bp-primary'}`} style={{ paddingLeft: 2 }}>
        <View className={`h-4 w-4 rounded-full bg-white ${manual ? '' : 'self-end'}`} />
      </View>
      <Text className="text-xs text-bp-textSecondary">
        {t('game.autoMarkOn') ?? 'Auto'} {manual ? `· ${t('game.manualMarking') ?? 'Manual'}` : ''}
      </Text>
    </Pressable>
  );
}

function reportTone(kind: string) {
  switch (kind) {
    case 'win':
      return 'border-bp-success40 bg-bp-success10';
    case 'banned':
    case 'error':
      return 'border-bp-danger40 bg-bp-danger10';
    default:
      return 'border-bp-gold40 bg-bp-gold10';
  }
}

function reportTextTone(kind: string) {
  switch (kind) {
    case 'win':
      return 'text-emerald-500';
    case 'banned':
    case 'error':
      return 'text-red-500';
    default:
      return 'text-amber-500';
  }
}