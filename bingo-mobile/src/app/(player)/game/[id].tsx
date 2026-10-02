import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { getApiErrorMessage } from '@/api/client';
import { CardTile } from '@/components/games/CardTile';
import { FairnessPanel } from '@/components/games/FairnessPanel';
import { StartCountdownBanner } from '@/components/games/StartCountdownBanner';
import { WinnerModal } from '@/components/games/WinnerModal';
import { NumberBoard } from '@/components/games/NumberBoard';
import { CardPickerModal } from '@/components/games/CardPickerModal';
import { useNumberAnnouncer } from '@/hooks/useNumberAnnouncer';
import { Button, Card, Modal, Screen, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useCountdown } from '@/hooks/useCountdown';
import { useGameWebSocket } from '@/hooks/useGameWebSocket';
import { patternProgress, patternCells } from '@/lib/pattern';
import { IconSettings } from '@/components/ui/icons';
import { useTheme } from '@/lib/theme';
import { useGameStore } from '@/store/game.store';
import {
  CardSort,
  MARK_COLORS,
  countMarkedRows,
  useGameSettings,
} from '@/store/gameSettings.store';
import { PlayerGameResponse, GameStateResponse, GameStatus, PlayerCardView } from '@/types';

export default function LiveGameScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const gameId = Number(id);
  const t = useTranslate();
  const router = useRouter();
  const { colors } = useTheme();
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
  const startTime = useGameStore((s) => s.startTime);
  const startReason = useGameStore((s) => s.startReason);
  const setPlayerCards = useGameStore((s) => s.setPlayerCards);
  const setRestartNotice = useGameStore((s) => s.setRestartNotice);
  const cardSort = useGameSettings((s) => s.cardSort);
  const markColor = useGameSettings((s) => s.markColor);

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
  const [busyCardId, setBusyCardId] = useState<number | null>(null);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedCardIds, setSelectedCardIds] = useState<Set<number>>(new Set());
  const [bulkBusy, setBulkBusy] = useState(false);
  const [patternPreviewOpen, setPatternPreviewOpen] = useState(false);
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

  /** Pay for one previewed card. Only that card's entry fee is charged. */
  const registerCard = async (cardId: number) => {
    if (busyCardId != null) return;
    setBusyCardId(cardId);
    setReport(null);
    try {
      await gamesApi.registerCard(gameId, cardId);
      setRegisterSuccess(true);
      // Registering from inside a game charges the entry fee, so the lobby
      // balance card must not keep showing the pre-registration total.
      void qc.invalidateQueries({ queryKey: ['wallet'] });
      await loadState();
    } catch (e) {
      setReport({ message: getApiErrorMessage(e), kind: 'error' });
    } finally {
      setBusyCardId(null);
    }
  };

  /**
   * Drop a card from the board. A preview was never paid for and simply goes
   * back to the pool; a registered card is unregistered and the entry fee is
   * refunded, so the balance is re-read either way.
   */
  const removeCard = async (cardId: number) => {
    if (busyCardId != null) return;
    setBusyCardId(cardId);
    setReport(null);
    try {
      const res = await gamesApi.removeCard(gameId, cardId);
      if (res.data.wasRegistered) {
        setRegisterSuccess(false);
        void qc.invalidateQueries({ queryKey: ['wallet'] });
        setReport({
          message:
            t('game.cardRemovedRefunded', { refund: String(res.data.refund) }) ??
            `Card removed. ${res.data.refund} refunded.`,
          kind: 'win',
        });
      }
      await loadState();
    } catch (e) {
      setReport({ message: getApiErrorMessage(e), kind: 'error' });
    } finally {
      setBusyCardId(null);
    }
  };

  const registrable = state?.status === GameStatus.REGISTRATION_OPEN;

  const toggleSelected = (cardId: number) => {
    setSelectedCardIds((prev) => {
      const next = new Set(prev);
      if (next.has(cardId)) next.delete(cardId);
      else next.add(cardId);
      return next;
    });
  };

  /** Long-press a card to start selecting several. Once selecting, a long-press
   *  just toggles that card instead of resetting the whole selection. */
  const startSelection = (cardId: number) => {
    if (!registrable) return;
    if (selectionMode) {
      toggleSelected(cardId);
      return;
    }
    setSelectionMode(true);
    setSelectedCardIds(new Set([cardId]));
  };

  const cancelSelection = () => {
    setSelectionMode(false);
    setSelectedCardIds(new Set());
  };

  /** Pay for every selected preview in one go. Previews only, so a selected
   *  already-registered card is skipped rather than charged twice. */
  const registerSelected = async () => {
    if (bulkBusy || busyCardId != null) return;
    const previews = state?.previewCards ?? [];
    const ids = previews.map((c) => c.cardId).filter((id) => selectedCardIds.has(id));
    if (ids.length === 0) return;
    setBulkBusy(true);
    setReport(null);
    let ok = 0;
    let firstError: string | null = null;
    for (const id of ids) {
      try {
        await gamesApi.registerCard(gameId, id);
        ok += 1;
      } catch (e) {
        if (!firstError) firstError = getApiErrorMessage(e);
      }
    }
    if (ok > 0) {
      setRegisterSuccess(true);
      void qc.invalidateQueries({ queryKey: ['wallet'] });
    }
    await loadState();
    setBulkBusy(false);
    cancelSelection();
    if (firstError) {
      setReport({ message: firstError, kind: 'error' });
    } else {
      setReport({
        message: t('game.bulkRegistered', { count: String(ok) }) ?? `${ok} card(s) registered.`,
        kind: 'win',
      });
    }
  };

  /** Drop every selected card: previews are released, registered ones refund. */
  const clearSelected = async () => {
    if (bulkBusy || busyCardId != null || selectedCardIds.size === 0) return;
    setBulkBusy(true);
    setReport(null);
    let removed = 0;
    let refunded = 0;
    let firstError: string | null = null;
    for (const id of selectedCardIds) {
      try {
        const res = await gamesApi.removeCard(gameId, id);
        removed += 1;
        if (res.data.wasRegistered) refunded += res.data.refund;
      } catch (e) {
        if (!firstError) firstError = getApiErrorMessage(e);
      }
    }
    if (refunded > 0) {
      setRegisterSuccess(false);
      void qc.invalidateQueries({ queryKey: ['wallet'] });
    }
    await loadState();
    setBulkBusy(false);
    cancelSelection();
    if (firstError) {
      setReport({ message: firstError, kind: 'error' });
    } else if (refunded > 0) {
      setReport({
        message:
          t('game.cardsRemovedRefunded', { count: String(removed), refund: String(refunded) }) ??
          `${removed} card(s) removed. ${refunded} refunded.`,
        kind: 'win',
      });
    } else {
      setReport({
        message: t('game.cardsCleared', { count: String(removed) }) ?? `${removed} card(s) removed.`,
        kind: 'win',
      });
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
  const patternCellsSet = winningPattern ? patternCells(winningPattern) : null;
  const patternLabel = winningPattern ? (t(`patterns.${winningPattern}`) ?? winningPattern) : null;
  const patternHint = winningPattern ? (t(`patterns.hint${winningPattern}`) ?? null) : null;

  const hasCards = (game.playerCards?.length ?? 0) > 0;
  const isLive = game.gameStatus === GameStatus.IN_PROGRESS;
  const isRegistration = game.gameStatus === GameStatus.REGISTRATION_OPEN;
  // The game status is only known once the state has loaded, so an unknown
  // status is the "still fetching" case rather than a game waiting to start.
  const isStatusUnknown = game.gameStatus == null;
  // Held but unpaid cards. The server drops these once registration closes, so a
  // card that was never registered can never linger into a live game.
  const previewCards = state?.previewCards ?? [];
  const selectedPreviewCount = previewCards.filter((c) => selectedCardIds.has(c.cardId)).length;
  const entryFee = meta?.entryFee;
  const markPaint = MARK_COLORS[markColor];

  // A player holding several cards should not have to hunt for the one closest
  // to winning, so the board is ordered by whichever signal they picked in game
  // settings. The sort is stable on card id, so a re-render never shuffles
  // cards that compare equal.
  const sortedPlayerCards = useMemo(() => {
    const cards = [...(game.playerCards ?? [])];
    if (cardSort === 'cardOrder') return cards;
    const marksFor = (c: PlayerCardView) => (isManual ? globalMarks : new Set(c.markedNumbers ?? []));
    const markedCount = (c: PlayerCardView) => marksFor(c).size;
    const rows = (c: PlayerCardView) => countMarkedRows(c.numbers, marksFor(c));
    const by: Record<Exclude<CardSort, 'cardOrder'>, (a: PlayerCardView, b: PlayerCardView) => number> = {
      mostMarked: (a, b) => markedCount(b) - markedCount(a),
      mostRows: (a, b) => rows(b) - rows(a),
    };
    return cards.sort((a, b) => by[cardSort](a, b) || a.cardId - b.cardId);
  }, [game.playerCards, cardSort, isManual, globalMarks]);

  return (
    <Screen>
      <View className="gap-1 py-1">
        <View className="flex-row items-center justify-between gap-3">
          <Text className="text-[10px] font-bold uppercase tracking-[0.2em] text-bp-textInactive">
            {t('game.winningPattern') ?? 'Winning pattern'}
          </Text>

          <View className="flex-row items-center gap-2">
            {/* The gear sits beside the prize rather than in the nav bar: these
                are board settings, and the board is directly below it. */}
            <Pressable
              onPress={() => router.push('/(player)/settings')}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel={t('gameSettings.title') ?? 'Settings'}
              className="h-8 w-8 items-center justify-center rounded-full border border-bp-borderInactive bg-bp-surface active:opacity-70"
            >
              <IconSettings color={colors.textSecondary} size={16} />
            </Pressable>
            <Text className="text-[10px] uppercase tracking-wider text-bp-textSecondary">
              {t('mobile.jackpotPrize') ?? 'Prize'}:
            </Text>
            <Text className="text-xl font-black text-bp-goldInk">
              {prize == null ? '—' : prize.toLocaleString()}
            </Text>
            {game.gameStatus ? <StatusPill status={game.gameStatus} /> : null}
          </View>
        </View>

        {/* The Amharic names run to 37 characters, so the pattern gets its own
            full-width row instead of a column squeezed beside the prize. It
            stays on one line, and shrinks rather than truncating. */}
        <Pressable
          onPress={() => setPatternPreviewOpen(true)}
          disabled={!winningPattern}
          className="w-full flex-row items-center gap-1.5 active:opacity-80"
        >
          <Text
            className="w-full text-sm font-black text-bp-goldInk"
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.75}
          >
            {patternLabel ?? '—'}
          </Text>
          {winningPattern ? (
            <Text className="text-xs text-bp-textInactive">ⓘ</Text>
          ) : null}
        </Pressable>
      </View>

      <View className="border-b border-bp-borderInactive bg-bp-bg pt-2 pb-1.5">
        <NumberBoard calledNumbers={calledNumbers} lastCalledNumber={lastCalledNumber} />
      </View>

      {/* Only meaningful once the player holds more than one card; the chosen
          order is theirs, not something the board should override. */}
      {(game.playerCards?.length ?? 0) > 1 ? (
        <View className="flex-row items-center gap-2 pt-2">
          <Text className="text-[10px] uppercase tracking-wider text-bp-textInactive">
            {t('gameSettings.cardSort') ?? 'Card order'}:
          </Text>
          <Text className="text-xs font-semibold text-bp-goldInk">
            {t(`gameSettings.sorts.${cardSort}`) ?? cardSort}
          </Text>
        </View>
      ) : null}

      <ScrollView
        style={{ flex: 1 }}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={loadState} tintColor="#6B5BFF" />}
        contentContainerClassName="gap-3 pb-8"
      >
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
            {hasCards || previewCards.length ? (
              <Card className="items-center py-4">
                <Text className="text-[10px] font-bold uppercase tracking-[0.25em] text-bp-textSecondary">
                  {t('game.youHold', { count: String(game.playerCards?.length ?? 0), fee: String(entryFee ?? '') }) ??
                    `You hold ${game.playerCards?.length ?? 0} cards`}
                </Text>
                {previewCards.length > 0 && (
                  <Text className="mt-1 text-[10px] text-bp-textSecondary">
                    {t('game.holdingForYou', { count: String(previewCards.length) }) ??
                      `${previewCards.length} card(s) held for you — not paid for yet`}
                  </Text>
                )}
                <Button variant="primary" className="mt-3" onPress={() => setPickerOpen(true)}>
                  {t('game.seeMoreCards') ?? '+ See More Cards'}
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

        {isRegistration && !selectionMode && (previewCards.length > 1 || hasCards) && (
          <Text className="text-center text-[10px] text-bp-textSecondary">
            {t('game.longPressToSelect') ?? 'Tip: long-press a card to select several'}
          </Text>
        )}

        {selectionMode && (
          <Card className="border-bp-primary40 bg-bp-primary10 gap-3">
            <View className="flex-row items-center justify-between">
              <Text className="text-sm font-bold text-bp-textPrimary">
                {t('game.selectCards', { count: String(selectedCardIds.size) }) ??
                  `${selectedCardIds.size} selected`}
              </Text>
              <Pressable onPress={cancelSelection} hitSlop={6} disabled={bulkBusy}>
                <Text className="text-xs font-bold text-bp-textSecondary">{t('common.cancel') ?? 'Cancel'}</Text>
              </Pressable>
            </View>
            <View className="flex-row" style={{ gap: 8 }}>
              {selectedPreviewCount > 0 && (
                <Button
                  variant="primary"
                  className="flex-1"
                  disabled={bulkBusy}
                  onPress={() => void registerSelected()}
                >
                  {t('game.registerSelected', { count: String(selectedPreviewCount) }) ??
                    `Register ${selectedPreviewCount}`}
                </Button>
              )}
              <Button
                variant="danger"
                className="flex-1"
                disabled={bulkBusy || selectedCardIds.size === 0}
                onPress={() => void clearSelected()}
              >
                {t('game.removeSelected', { count: String(selectedCardIds.size) }) ??
                  `Remove ${selectedCardIds.size}`}
              </Button>
            </View>
          </Card>
        )}

        {(hasCards || previewCards.length > 0) && (
          <View className="flex-row flex-wrap" style={{ gap: 10 }}>
            {/* Previews first: they are what the player is being asked to decide on. */}
            {previewCards.map((card) => (
              <CardTile
                key={`preview-${card.cardId}`}
                card={card}
                tone="preview"
                t={t}
                cardIdLabel={t('game.cardNumber', { id: String(card.cardId) }) ?? `Card #${card.cardId}`}
                busy={busyCardId === card.cardId || bulkBusy}
                selectable={selectionMode}
                selected={selectedCardIds.has(card.cardId)}
                onSelectToggle={() => toggleSelected(card.cardId)}
                onLongPressCard={() => startSelection(card.cardId)}
                onRegister={() => void registerCard(card.cardId)}
                onRemove={() => void removeCard(card.cardId)}
              />
            ))}
            {sortedPlayerCards.map((card) => {
              const marked = isManual ? globalMarks : new Set(card.markedNumbers ?? []);
              const prog = isManual ? patternProgress(card.numbers, marked, winningPattern) : null;
              const done = prog?.done;
              const total = prog?.total;
              const patternDone = isManual && total != null && done === total;
              const claimable = isLive && !card.banned && !card.winner && !claiming;
              const tone = card.banned
                ? ('banned' as const)
                : card.winner
                  ? ('winner' as const)
                  : claimable
                    ? ('live' as const)
                    : ('registered' as const);
              return (
                <CardTile
                  key={card.cardId}
                  card={card}
                  tone={tone}
                  t={t}
                  cardIdLabel={t('game.cardNumber', { id: String(card.cardId) }) ?? `Card #${card.cardId}`}
                  called={calledNumbers}
                  marked={isManual ? [...marked] : []}
                  lastCalledNumber={lastCalledNumber}
                  interactive={!selectionMode && isManual && !card.banned && !card.winner}
                  onToggleMark={(n) => void toggleMark(n)}
                  busy={claimable && claiming}
                  onClaim={() => void claimCard(card)}
                  onRemove={isRegistration ? () => void removeCard(card.cardId) : undefined}
                  selectable={selectionMode}
                  selected={selectedCardIds.has(card.cardId)}
                  onSelectToggle={() => toggleSelected(card.cardId)}
                  onLongPressCard={() => startSelection(card.cardId)}
                  markColor={markPaint}
                  footer={
                    <>
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
                    </>
                  }
                />
              );
            })}
          </View>
        )}
        {/* A card the player already paid for is shown at every status, so a
            card survives STARTING and stays on the board once the game is
            live. Only a game that never reached registration has nothing to
            show, and that is the one case worth a message. */}
        {!hasCards && previewCards.length === 0 && !isRegistration && !isStatusUnknown ? (
          <Card>
            <Text className="text-center text-sm text-bp-textSecondary">
              {game.gameStatus === GameStatus.ENDED
                ? t('game.noCardsThisGame') ?? 'No cards in this game'
                : t('game.waitingForRegistration') ??
                  'No cards yet. They will appear here when registration opens.'}
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
          onPreviewed={() => void loadState()}
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

      {patternPreviewOpen && winningPattern && (
        <Modal onClose={() => setPatternPreviewOpen(false)}>
          <Card className="gap-3">
            <View className="flex-row items-center justify-between">
              <Text
                className="min-w-0 flex-1 text-base font-black text-bp-goldInk"
                numberOfLines={1}
                adjustsFontSizeToFit
                minimumFontScale={0.7}
              >
                {patternLabel}
              </Text>
              <Pressable
                onPress={() => setPatternPreviewOpen(false)}
                className="ml-2 h-8 w-8 items-center justify-center rounded-full bg-bp-surfaceAlt active:opacity-70"
              >
                <Text className="text-base text-bp-textSecondary">✕</Text>
              </Pressable>
            </View>
            <View className="items-center">
              <MiniPattern cells={patternCellsSet} size={34} />
            </View>
            {patternHint ? (
              <Text className="text-center text-sm leading-relaxed text-bp-textSecondary">{patternHint}</Text>
            ) : null}
            {patternCellsSet?.size ? (
              <Text className="text-center text-[11px] text-bp-textInactive">
                {patternCellsSet.size} {t('game.patternCells') ?? 'cells'}
              </Text>
            ) : null}
          </Card>
        </Modal>
      )}

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