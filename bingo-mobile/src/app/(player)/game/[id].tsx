import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AppState, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { getApiErrorMessage } from '@/api/client';
import { CardTile } from '@/components/games/CardTile';
import { FairnessPanel } from '@/components/games/FairnessPanel';
import { StartCountdownBanner } from '@/components/games/StartCountdownBanner';
import { ResultsBoard } from '@/components/games/ResultsBoard';
import { NumberBoard } from '@/components/games/NumberBoard';
import { PendingClaimCards } from '@/components/games/PendingClaimCards';
import { CardCountFan } from '@/components/games/CardCountFan';
import { useNumberAnnouncer } from '@/hooks/useNumberAnnouncer';
import { Button, Card, Modal, Screen, StatusPill } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useCountdown, useCountdownTo, MAX_CLAIM_WINDOW_SECONDS, DEFAULT_CLAIM_WINDOW_SECONDS } from '@/hooks/useCountdown';
import { useGameWebSocket } from '@/hooks/useGameWebSocket';
import { patternProgress, patternCells } from '@/lib/pattern';
import { useTheme } from '@/lib/theme';
import { useGameStore } from '@/store/game.store';
import { useAuthStore } from '@/store/auth.store';
import {
  CardSort,
  MARK_COLORS,
  countMarkedRows,
  countSmallCrosses,
  countSmallSquares,
  countTs,
  useGameSettings,
} from '@/store/gameSettings.store';
import { PlayerGameResponse, GameStateResponse, GameStatus, PendingClaimCard, PlayerCardView } from '@/types';

/** How long a confirmation stays on the board before it clears itself. */
const TOAST_MS = 3_000;

export default function LiveGameScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const gameId = Number(id);
  const t = useTranslate();
  const qc = useQueryClient();
  const router = useRouter();
  const { colors } = useTheme();

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
  // Auto-mark is a general preference now, so the board follows it in every
  // game rather than remembering a per-game choice. The server still owns the
  // per-card flag, so it is pushed into step below rather than assumed.
  const autoMarkPreferred = useGameSettings((s) => s.autoMark);
  const autoMarkSync = useRef<{ key: string; tries: number } | null>(null);

  const game = useMemo(
    () => ({
      gameStatus,
      calledNumbers: calledNumberEntries,
      playerCards,
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
  const [busy, setBusy] = useState(false);
  const [patternPreviewOpen, setPatternPreviewOpen] = useState(false);
  const marksSeeded = useRef(false);

  useGameWebSocket(gameId);

  /**
   * Confirmations here are feedback on a tap, not state: the card list below
   * already shows whether a card is registered, and leaving a banner up just
   * pushes the board down. Both clear themselves on a timer, and each new
   * message restarts it, so a second action does not cut the first one short.
   */
  useEffect(() => {
    if (!report) return;
    const timer = setTimeout(() => setReport(null), TOAST_MS);
    return () => clearTimeout(timer);
  }, [report]);

  useEffect(() => {
    if (!registerSuccess) return;
    const timer = setTimeout(() => setRegisterSuccess(false), TOAST_MS);
    return () => clearTimeout(timer);
  }, [registerSuccess]);

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

  // The cards that have claimed Bingo and are waiting on an admin decision. A
  // claim is never just "someone won": the paused game shows the actual card with
  // every called number marked, so the claim can be checked by the players instead
  // of taken on trust. The claimer's own card is pointed out in the panel.
  const [pendingClaimCards, setPendingClaimCards] = useState<PendingClaimCard[]>([]);
  // Claims the player has put away. An approved or rejected claim takes itself off
  // the game, so the panel closes on its own; this is only for the player who does
  // not want to look at it while the game waits.
  const [dismissedClaims, setDismissedClaims] = useState<number[]>([]);
  const currentPlayerId = useAuthStore((s) => s.user?.id ?? null);
  const claimsPending = gameStatus === GameStatus.CLAIM_PENDING || claimPending != null;

  const loadPendingClaimCards = useCallback(async () => {
    try {
      const res = await gamesApi.getPendingClaimCards(gameId);
      setPendingClaimCards(res.data);
      // A dismissed claim that has since been decided is no longer on the game, so
      // stop remembering it; only claims still waiting can stay dismissed.
      setDismissedClaims((prev) => prev.filter((id) => res.data.some((c) => c.claimId === id)));
    } catch {
      setPendingClaimCards([]);
    }
  }, [gameId]);

  // Only polled while a claim is waiting. Approving or rejecting one takes it off
  // the game, and the panel has to leave with it instead of lingering. A decision
  // arrives on the socket as the claim being cleared, which re-runs this and drops
  // that card at once; the poll is the backstop for a dropped session.
  useEffect(() => {
    if (!claimsPending) return;
    const id = setTimeout(() => void loadPendingClaimCards(), 0);
    const poll = setInterval(() => void loadPendingClaimCards(), 4_000);
    return () => {
      clearTimeout(id);
      clearInterval(poll);
    };
  }, [claimsPending, claimPending, loadPendingClaimCards]);

  // Cards already claimed are hidden rather than cleared, so the state is never
  // written outside a fetch and the panel cannot flash an old claim on the way out.
  const visibleClaimCards = claimsPending
    ? pendingClaimCards.filter((c) => !dismissedClaims.includes(c.claimId))
    : [];

  // Countdown and winner result
  const isCountingDown = gameStatus === GameStatus.STARTING;
  const countdownSeconds = useCountdown(startTime, isCountingDown);
  const claimWindowActive = gameStatus === GameStatus.CLAIM_PENDING;
  const claimWindowEnds = state?.claimWindowEndsAt ?? null;
  const claimWindowSeconds = useCountdownTo(
    claimWindowEnds,
    claimWindowActive,
    MAX_CLAIM_WINDOW_SECONDS,
    DEFAULT_CLAIM_WINDOW_SECONDS
  );

  const isManual = !autoMarkPreferred;
  const lastCalledNumber =
    game.calledNumbers.length > 0 ? game.calledNumbers[game.calledNumbers.length - 1].number : null;

  // Switching a game to auto-mark hands the marking back to the server, so the
  // numbers already called have to be carried across first or the board would
  // come back empty. Mirrors what the old per-game toggle did.
  const applyAutoMark = useCallback(
    async (enabled: boolean, cards: PlayerCardView[]) => {
      if (enabled) {
        setGlobalMarks(new Set());
        for (const card of cards) {
          await gamesApi.saveMarks(gameId, card.cardId, [], true);
        }
        return;
      }
      const ported = new Set<number>();
      for (const card of cards) {
        for (const row of card.numbers) {
          for (const num of row) {
            if (calledNumberEntries.some((entry) => entry.number === num)) ported.add(num);
          }
        }
      }
      ported.add(0);
      setGlobalMarks(ported);
      for (const card of cards) {
        await gamesApi.saveMarks(gameId, card.cardId, [...ported], false);
      }
    },
    [gameId, calledNumberEntries, setGlobalMarks]
  );

  // The board follows the stored preference, but the server's per-card flag is
  // what actually decides who marks a number, so the two are reconciled here
  // rather than assumed to agree. Retries are bounded: the screen polls every
  // four seconds, and a switch the server keeps refusing would otherwise be
  // retried for as long as the game is open.
  useEffect(() => {
    const cards = state?.playerCards ?? [];
    if (!state || cards.length === 0) return;
    if (state.autoMark === autoMarkPreferred) return;
    const key = `${gameId}:${autoMarkPreferred}`;
    const attempt = autoMarkSync.current;
    if (attempt && attempt.key === key && attempt.tries >= 3) return;
    autoMarkSync.current = {
      key,
      tries: attempt && attempt.key === key ? attempt.tries + 1 : 1,
    };
    void applyAutoMark(autoMarkPreferred, cards)
      .then(() => loadState())
      .catch(() => {
        // Nothing to report here: the board still follows the preference, and
        // the marks the player can make are the ones already called.
      });
  }, [state, autoMarkPreferred, applyAutoMark, gameId, loadState]);

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
        setReport({
          message:
            t('game.claimBanned', {
              cardId: String(card.cardId),
              lastNumber: lastCalledNumber != null ? String(lastCalledNumber) : '—',
            }) ?? 'This card is banned',
          kind: 'banned',
        });
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
  useNumberAnnouncer(calledNumbers);
  const prize = state?.prizeAmount ?? game.prizeAmount;
  const winningPattern = state?.winningPattern ?? null;
  const patternCellsSet = winningPattern ? patternCells(winningPattern) : null;
  const patternLabel = winningPattern ? (t(`patterns.${winningPattern}`) ?? winningPattern) : null;
  const patternHint = winningPattern ? (t(`patterns.hint${winningPattern}`) ?? null) : null;

  const hasCards = (game.playerCards?.length ?? 0) > 0;
  const isLive = game.gameStatus === GameStatus.IN_PROGRESS;
  const isClaimPending = game.gameStatus === GameStatus.CLAIM_PENDING;
  const isRegistration = game.gameStatus === GameStatus.REGISTRATION_OPEN;
  const isEnded = game.gameStatus === GameStatus.ENDED;
  const isStatusUnknown = game.gameStatus == null;
  const canSeeNextGame = isLive || isClaimPending || isEnded;

  /**
   * Once a game is over the player has nothing left to do on this screen, but
   * the admin can open the next one at any moment. Watching for it here means
   * they do not have to navigate back out and back in to find out. The poll
   * only runs while the game is over: the endpoint excludes ENDED games, so
   * during a live game it could never surface anything new.
   *
   * When the game is live and a next game has opened registration, the player
   * is shown a banner to jump to it — they can watch the live game and register
   * for the next one without navigating out.
   */
  const nextGamesQuery = useQuery({
    queryKey: ['player/games'],
    queryFn: () => gamesApi.getActive(),
    enabled: canSeeNextGame,
    refetchInterval: canSeeNextGame ? 10_000 : false,
  });
  const nextGame: PlayerGameResponse | undefined = (nextGamesQuery.data?.data ?? []).find(
    (g) => g.id !== gameId && g.status === GameStatus.REGISTRATION_OPEN
  );
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
    const rows = (c: PlayerCardView) => countMarkedRows(c.numbers, marksFor(c));
    const squares = (c: PlayerCardView) => countSmallSquares(c.numbers, marksFor(c));
    const smallCrosses = (c: PlayerCardView) => countSmallCrosses(c.numbers, marksFor(c));
    const ts = (c: PlayerCardView) => countTs(c.numbers, marksFor(c));
    const calledSetAll = new Set(calledNumbers);
    const calledCount = (c: PlayerCardView) => {
      const nums = c.numbers?.flat() ?? [];
      let cnt = 0;
      for (const n of nums) {
        if (n != null && n >= 0 && calledSetAll.has(n)) cnt++;
      }
      return cnt;
    };
    const by: Record<Exclude<CardSort, 'cardOrder'>, (a: PlayerCardView, b: PlayerCardView) => number> = {
      mostCalled: (a, b) => calledCount(b) - calledCount(a),
      mostRows: (a, b) => rows(b) - rows(a),
      mostSquares: (a, b) => squares(b) - squares(a),
      mostSmallCrosses: (a, b) => smallCrosses(b) - smallCrosses(a),
      mostTs: (a, b) => ts(b) - ts(a),
    };
    return cards.sort((a, b) => by[cardSort](a, b) || a.cardId - b.cardId);
  }, [game.playerCards, cardSort, isManual, globalMarks, calledNumbers]);

  const topHints = useMemo(() => {
    if (!game.playerCards || game.playerCards.length === 0 || cardSort === 'cardOrder') {
      return new Map<number, string>();
    }
    const marksFor = (c: PlayerCardView) => (isManual ? globalMarks : new Set(c.markedNumbers ?? []));
    const rows = (c: PlayerCardView) => countMarkedRows(c.numbers, marksFor(c));
    const squares = (c: PlayerCardView) => countSmallSquares(c.numbers, marksFor(c));
    const smallCrosses = (c: PlayerCardView) => countSmallCrosses(c.numbers, marksFor(c));
    const ts = (c: PlayerCardView) => countTs(c.numbers, marksFor(c));
    const calledSetAll = new Set(calledNumbers);
    const calledCount = (c: PlayerCardView) => {
      const nums = c.numbers?.flat() ?? [];
      let cnt = 0;
      for (const n of nums) {
        if (n != null && n >= 0 && calledSetAll.has(n)) cnt++;
      }
      return cnt;
    };
    const topVals = new Set<number>();
    for (const c of game.playerCards) {
      let v = 0;
      if (cardSort === 'mostCalled') v = calledCount(c);
      if (cardSort === 'mostRows') v = rows(c);
      if (cardSort === 'mostSquares') v = squares(c);
      if (cardSort === 'mostSmallCrosses') v = smallCrosses(c);
      if (cardSort === 'mostTs') v = ts(c);
      if (v > 0) topVals.add(v);
    }
    const max = topVals.size > 0 ? Math.max(...topVals) : 0;
    if (max === 0) return new Map<number, string>();
    const hintMap = new Map<number, string>();
    for (const c of game.playerCards) {
      let v = 0;
      let prefix = 'L';
      if (cardSort === 'mostCalled') { v = calledCount(c); prefix = 'C'; }
      if (cardSort === 'mostSquares') { v = squares(c); prefix = 'S'; }
      if (cardSort === 'mostSmallCrosses') { v = smallCrosses(c); prefix = 'X'; }
      if (cardSort === 'mostTs') { v = ts(c); prefix = 'T'; }
      if (cardSort === 'mostRows') { v = rows(c); prefix = 'L'; }
      if (v === max && v > 0) {
        hintMap.set(c.cardId, `${prefix}-${v}`);
      }
    }
    return hintMap;
  }, [game.playerCards, cardSort, isManual, globalMarks, calledNumbers]);

  return (
    <Screen>
      <View className="gap-1 py-1">
        <View className="flex-row items-center justify-between gap-3">
          <Text className="text-[10px] font-bold uppercase tracking-[0.2em]" style={{ color: colors.textInactive }}>
            {t('game.winningPattern') ?? 'Winning pattern'}
          </Text>

          <View className="flex-row items-center gap-2">
            <View className="rounded-full border px-2 py-0.5" style={{ borderColor: '#8B5E3C30', backgroundColor: '#8B5E3C10' }}>
              <Text className="text-[10px] font-black" style={{ color: colors.gold }}>
                {t('mobile.jackpotPrize') ?? 'Prize'}: {prize == null ? '—' : prize.toLocaleString()}
              </Text>
            </View>
            <View className="rounded-full border px-2 py-0.5" style={{ borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt }}>
              <Text className="text-[10px] font-black" style={{ color: colors.textPrimary }}>
                {t('game.priceLabel') ?? 'Price'}: {entryFee != null ? entryFee : '—'}
              </Text>
            </View>
            <StatusPill status={game.gameStatus ?? 'WAITING'} />
          </View>
        </View>

        <Pressable
          onPress={() => setPatternPreviewOpen(true)}
          disabled={!winningPattern}
          className="w-full flex-row items-center gap-1.5 active:opacity-80"
        >
          <Text
            className="w-full text-sm font-black"
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.75}
            style={{ color: colors.gold }}
          >
            {patternLabel ?? '—'}
          </Text>
          {winningPattern ? (
            <Text className="text-xs" style={{ color: colors.textInactive }}>ⓘ</Text>
          ) : null}
        </Pressable>
      </View>

      <View className="border-b pt-2 pb-1.5" style={{ borderColor: colors.borderInactive, backgroundColor: colors.bg }}>
        <NumberBoard
          calledNumbers={calledNumbers}
          lastCalledNumber={lastCalledNumber}
          registeredCount={game.playerCards?.length ?? 0}
        />
      </View>

      <ScrollView
        style={{ flex: 1 }}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={loadState} tintColor="#6B5BFF" />}
        // The bottom padding leaves room for the floating add-card button, so
        // it never sits on top of the last card in the list.
        contentContainerClassName="gap-3 pb-24"
      >
        <FairnessPanel gameId={gameId} status={game.gameStatus} liveHash={state?.fairnessHash} />

        <StartCountdownBanner status={game.gameStatus} reason={startReason} seconds={countdownSeconds} />

        {game.gameStatus === GameStatus.ENDED && (
          <>
            <Card
              style={{
                borderColor: state?.isWinner ? '#36E4B440' : colors.borderInactive,
                backgroundColor: state?.isWinner ? '#36E4B510' : colors.surface,
              }}
            >
              <Text
                className="text-center text-base font-bold"
                style={{ color: state?.isWinner ? '#059669' : colors.textSecondary }}
              >
                {state?.isWinner ? t('game.youWon') ?? '🎉 BINGO! You Won! 🎉' : (t('game.gameOver') ?? 'Game Over')}
              </Text>
            </Card>

            {/* The room's results: every winner's card (tap to open it) and every
                banned card. No admin decided this — the engine auto-validated every
                claim, so what the room sees is what was proven. */}
            <ResultsBoard
              winnerCards={state?.winnerCards ?? []}
              bannedCards={state?.bannedCards ?? []}
              calledNumbers={calledNumbers}
            />
          </>
        )}

        {/* When the current game is live and the admin has opened registration
            for the next one, the player is offered a way in without leaving the
            board: they can keep watching the live calls and jump to register
            for the next game in a single tap. */}
        {(isLive || isClaimPending) && nextGame && (
          <Card className="gap-3" style={{ borderColor: '#6B5BFF30', backgroundColor: '#6B5BFF10' }}>
            <View className="items-center gap-1">
              <Text className="text-center text-sm font-bold" style={{ color: colors.textPrimary }}>
                {t('game.nextGameOpen') ?? 'A new game is open'}
              </Text>
              <Text className="text-center text-xs" style={{ color: colors.textSecondary }}>
                {t('mobile.jackpotPrize') ?? 'Prize'}: {nextGame.prizeAmount ?? nextGame.entryFee * 5} ·{' '}
                {t('game.priceLabel') ?? 'Price'}: {nextGame.entryFee}
              </Text>
            </View>
            <Button
              onPress={async () => {
                try {
                  setBusy(true);
                  await gamesApi.previewPreviousCards(nextGame.id);
                  router.push({ pathname: '/(player)/game/[id]', params: { id: String(nextGame.id) } });
                } catch (e) {
                  setReport({ kind: 'error', message: getApiErrorMessage(e) });
                } finally {
                  setBusy(false);
                }
              }}
              disabled={busy}
              style={{ flex: 1 }}
              variant="primary"
            >
              {t('game.joinNextGame') ?? 'Register for the new game'}
            </Button>
          </Card>
        )}

        {/* The finished game stays on screen, but the moment the admin opens the
            next one the player gets a way straight into its registration
            instead of having to navigate out and back in. */}
        {isEnded && nextGame && (
          <Card className="gap-3" style={{ borderColor: '#6B5BFF30', backgroundColor: '#6B5BFF10' }}>
            <View className="items-center gap-1">
              <Text className="text-center text-sm font-bold" style={{ color: colors.textPrimary }}>
                {t('game.nextGameOpen') ?? 'A new game is open'}
              </Text>
              <Text className="text-center text-xs" style={{ color: colors.textSecondary }}>
                {t('mobile.jackpotPrize') ?? 'Prize'}: {nextGame.prizeAmount ?? nextGame.entryFee * 5} ·{' '}
                {t('game.priceLabel') ?? 'Price'}: {nextGame.entryFee}
              </Text>
            </View>
            <View className="flex-row gap-2">
              <Button
                onPress={async () => {
                  try {
                    setBusy(true);
                    await gamesApi.previewPreviousCards(nextGame.id);
                    router.push({ pathname: '/(player)/game/[id]', params: { id: String(nextGame.id) } });
                  } catch (e) {
                    setReport({ kind: 'error', message: getApiErrorMessage(e) });
                  } finally {
                    setBusy(false);
                  }
                }}
                disabled={busy}
                style={{ flex: 1 }}
                variant="primary"
              >
                {t('game.reusePreviousCards') ?? 'Reuse previous cards'}
              </Button>
              <Button
                onPress={() => router.push({ pathname: '/(player)/game/[id]', params: { id: String(nextGame.id) } })}
                disabled={busy}
                style={{ flex: 1 }}
                variant="outline"
              >
                {t('game.getNewCards') ?? 'Get new cards'}
              </Button>
            </View>
          </Card>
        )}

        {claimWindowActive && (
          <Card className="gap-3" style={{ borderColor: '#8B5E3C40', backgroundColor: '#8B5E3C10' }}>
            <Text className="text-center text-xs font-bold uppercase tracking-[0.16em]" style={{ color: colors.gold }}>
              {t('game.claimWindowTitle') ?? 'Bingo claim window'}
            </Text>
            <Text className="text-center text-4xl font-black" style={{ color: colors.gold }}>
              {claimWindowSeconds}
            </Text>
            <Text className="text-center text-xs" style={{ color: colors.gold }}>
              {t('game.claimWindowHint') ?? 'Others can still claim Bingo until this timer ends.'}
            </Text>
          </Card>
        )}

        {/* What is actually under review: the claimed cards with every called
            number marked, instead of a bare "claim pending" line. The panel goes
            away by itself once the claim is decided, or on the ✕ if the player
            would rather not look at it. */}
        <PendingClaimCards
          claims={visibleClaimCards}
          currentPlayerId={currentPlayerId}
          onDismiss={() => setDismissedClaims(visibleClaimCards.map((c) => c.claimId))}
        />

        {report && (
          <Card style={{ borderColor: report.kind === 'win' ? '#36E4B440' : report.kind === 'banned' || report.kind === 'error' ? '#FF5C6C40' : '#8B5E3C40', backgroundColor: report.kind === 'win' ? '#36E4B510' : report.kind === 'banned' || report.kind === 'error' ? '#FF5C6C10' : '#8B5E3C10' }}>
            <Text className="text-center text-sm font-semibold" style={{ color: report.kind === 'win' ? '#059669' : report.kind === 'banned' || report.kind === 'error' ? '#dc2626' : '#d97706' }}>{report.message}</Text>
          </Card>
        )}

        {isRegistration && (
          <>
            
            {registerSuccess && (
              <Card className="px-3 py-2" style={{ borderColor: '#36E4B440', backgroundColor: '#36E4B510' }}>
                <Text className="text-center text-emerald-500 text-sm font-semibold">✓ {t('game.registeredReady') ?? 'Registered!'}</Text>
              </Card>
            )}
          </>
        )}

        {selectionMode && (
          <Card className="gap-3" style={{ borderColor: '#6B5BFF30', backgroundColor: '#6B5BFF10' }}>
            <View className="flex-row items-center justify-between">
              <View className="flex-row items-center gap-2">
                <Pressable
                  onPress={() => {
                    if (bulkBusy) return;
                    // no-op lint cleanup
                    // Consider everything selectable in this context? But selection
                    // can only act on what exists; better: select all that are
                    // shown/selectable (previews + registered if any, but we can
                    // target previews for register, and any for remove). For a
                    // checkbox: if nothing or partial selected, select everything.
                    // unused: totalSelectable calculation removed to satisfy lint
                    // easier: select all previews and all player cards that exist
                    const all = new Set<number>();
                    for (const pc of game.playerCards ?? []) all.add(pc.cardId);
                    for (const pr of previewCards) all.add(pr.cardId);
                    if (all.size === 0) return;
                    if (selectedCardIds.size === all.size) {
                      setSelectedCardIds(new Set());
                      return;
                    }
                    setSelectedCardIds(all);
                  }}
                  disabled={bulkBusy}
                  hitSlop={6}
                  className="flex-row items-center gap-1.5"
                >
                  <Text className="text-sm">
                    {(() => {
                      const all = new Set<number>();
                      for (const pc of game.playerCards ?? []) all.add(pc.cardId);
                      for (const pr of previewCards) all.add(pr.cardId);
                      const allSelected = all.size > 0 && selectedCardIds.size === all.size;
                      return allSelected ? '☑' : '☐';
                    })()}
                  </Text>
                  <Text className="text-xs" style={{ color: colors.textSecondary }}>
                    {(() => {
                      const all = new Set<number>();
                      for (const pc of game.playerCards ?? []) all.add(pc.cardId);
                      for (const pr of previewCards) all.add(pr.cardId);
                      const allSelected = all.size > 0 && selectedCardIds.size === all.size;
                      return allSelected
                        ? (t('game.unselectAll') ?? 'Unselect all')
                        : (t('game.selectAll') ?? 'Select all');
                    })()}
                  </Text>
                </Pressable>
                <Text className="text-sm font-bold" style={{ color: colors.textPrimary }}>
                  {t('game.selectCards', { count: String(selectedCardIds.size) }) ??
                    `${selectedCardIds.size} selected`}
                </Text>
              </View>
              <Pressable onPress={cancelSelection} hitSlop={6} disabled={bulkBusy}>
                <Text className="text-xs font-bold" style={{ color: colors.textSecondary }}>{t('common.cancel') ?? 'Cancel'}</Text>
              </Pressable>
            </View>
            {/* Show bulk actions when nothing is selected: "Register all previews"
                and "Remove all selected". When some are selected, still allow
                register all previews or remove all selected (which matches what
                the count showed before). */}
            <View className="flex-row flex-wrap" style={{ gap: 8 }}>
              <Button
                variant="primary"
                className="flex-1"
                disabled={bulkBusy || previewCards.length === 0}
                onPress={() => {
                  // If nothing selected, select all previews and register them.
                  if (selectedCardIds.size === 0 && previewCards.length > 0) {
                    setSelectedCardIds(new Set(previewCards.map((c) => c.cardId)));
                    // Register after setting selection to cover all previews.
                    setTimeout(() => void registerSelected(), 0);
                    return;
                  }
                  void registerSelected();
                }}
              >
                {selectedCardIds.size === 0
                  ? (t('game.registerAllPreviews') ?? 'Register all')
                  : (t('game.registerSelected', { count: String(selectedPreviewCount) }) ??
                      `Register ${selectedPreviewCount}`)}
              </Button>
              <Button
                variant="danger"
                className="flex-1"
                disabled={bulkBusy || selectedCardIds.size === 0}
                onPress={() => {
                  if (selectedCardIds.size === 0) return;
                  void clearSelected();
                }}
              >
                {selectedCardIds.size === 0
                  ? (t('game.removeAllSelected') ?? 'Remove all')
                  : (t('game.removeSelected', { count: String(selectedCardIds.size) }) ??
                      `Remove ${selectedCardIds.size}`)}
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
                  cardIdLabel={topHints.get(card.cardId) ?? (t('game.cardNumber', { id: String(card.cardId) }) ?? `Card #${card.cardId}`)}
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
                  onLongPressCard={card.banned || card.winner ? undefined : () => startSelection(card.cardId)}
                  markColor={markPaint}
                  footer={
                    <>
                      {isManual && total != null ? (
                        <View
                          className="self-center rounded-full border px-2 py-0.5"
                          style={{
                            borderColor: patternDone ? '#8B5E3C40' : colors.borderInactive,
                            backgroundColor: patternDone ? '#8B5E3C10' : colors.surfaceAlt,
                          }}
                        >
                          <Text
                            className="text-[9px] font-bold"
                            style={{ color: patternDone ? colors.gold : colors.textSecondary }}
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
            show, and that is the one case worth a message. A mid-game joiner
            with no cards sees a hint to register for the next game. */}
        {!hasCards && previewCards.length === 0 && !isRegistration && !isStatusUnknown ? (
          <Card>
            <Text className="text-center text-sm" style={{ color: colors.textSecondary }}>
              {game.gameStatus === GameStatus.ENDED
                ? (t('game.noCardsThisGame') ?? 'No cards in this game')
                : (isLive || isClaimPending
                    ? (t('game.noCardsWatchLive') ?? 'You have no cards in this game. Watch the live calls and register for the next game.')
                    : null)}
            </Text>
          </Card>
        ) : null}

        </ScrollView>

      {/* Adding a card used to be a button inside the registration card, which
          meant scrolling to find it every time and a different label depending
          on whether the player already held one. A single floating action does
          both jobs, and it stays reachable while the cards are being read. */}
      {isRegistration && !selectionMode ? (
        <Pressable
          onPress={() => setPickerOpen((open) => !open)}
          accessibilityRole="button"
          accessibilityLabel={t('game.chooseCard') ?? 'Add a card'}
          hitSlop={8}
          className="absolute bottom-5 right-4 h-16 w-16 items-center justify-center rounded-full active:opacity-80"
          style={{ backgroundColor: colors.primary, shadowColor: '#000', shadowOpacity: 0.3, shadowRadius: 8, shadowOffset: { width: 0, height: 4 }, elevation: 6 }}
        >
          <Text className="text-4xl font-light leading-10 text-white">+</Text>
        </Pressable>
      ) : null}

      {/* The count choices fan straight out of the button that opened them,
          so picking is one tap instead of open-a-dialog then confirm. */}
      {isRegistration && !selectionMode && pickerOpen && (
        <CardCountFan
          gameId={gameId}
          entryFee={entryFee ?? 0}
          onClose={() => setPickerOpen(false)}
          onPreviewed={() => void loadState()}
        />
      )}
      {patternPreviewOpen && winningPattern && (
        <Modal onClose={() => setPatternPreviewOpen(false)}>
          <Card className="gap-3">
            <View className="flex-row items-center justify-between">
              <Text
                className="min-w-0 flex-1 text-base font-black"
                numberOfLines={1}
                adjustsFontSizeToFit
                minimumFontScale={0.7}
                style={{ color: colors.gold }}
              >
                {patternLabel}
              </Text>
              <Pressable
                onPress={() => setPatternPreviewOpen(false)}
                className="ml-2 h-8 w-8 items-center justify-center rounded-full active:opacity-70"
                style={{ backgroundColor: colors.surfaceAlt }}
              >
                <Text className="text-base" style={{ color: colors.textSecondary }}>✕</Text>
              </Pressable>
            </View>
            <View className="items-center">
              <MiniPattern cells={patternCellsSet} size={34} colors={{ bg: colors.bg, primary: colors.primary, gold: colors.gold, surface: colors.surface }} />
            </View>
            {patternHint ? (
              <Text className="text-center text-sm leading-relaxed" style={{ color: colors.textSecondary }}>{patternHint}</Text>
            ) : null}
            {patternCellsSet?.size ? (
              <Text className="text-center text-[11px]" style={{ color: colors.textInactive }}>
                {patternCellsSet.size} {t('game.patternCells') ?? 'cells'}
              </Text>
            ) : null}
          </Card>
        </Modal>
      )}

    </Screen>
  );
}

function MiniPattern({ cells, size = 11, colors }: { cells: Set<string> | null; size?: number; colors: { bg: string; primary: string; gold: string; surface: string } }) {
  const gap = 3;
  const pad = 6;
  const boardWidth = size * 5 + gap * 4 + pad * 2;
  return (
    <View
      className="rounded-xl border"
      style={{ padding: pad, gap, width: boardWidth, borderColor: '#8B5E3C20', backgroundColor: colors.bg }}
    >
      {Array.from({ length: 5 }, (_, r) => (
        <View key={r} className="flex-row" style={{ gap }}>
          {Array.from({ length: 5 }, (_, c) => {
            const isFree = r === 2 && c === 2;
            const needed = cells?.has(`${r},${c}`);
            const cellBg = isFree ? colors.gold : needed ? colors.primary : colors.surface;
            const shadow = isFree
              ? { boxShadow: '0 0 5px rgba(242,201,76,0.65)' }
              : needed
                ? { boxShadow: '0 0 5px rgba(107,91,255,0.8)' }
                : {};
            return (
              <View
                key={c}
                style={{
                  width: size,
                  height: size,
                  borderRadius: 2.5,
                  backgroundColor: cellBg,
                  ...shadow,
                }}
              />
            );
          })}
        </View>
      ))}
    </View>
   );
}