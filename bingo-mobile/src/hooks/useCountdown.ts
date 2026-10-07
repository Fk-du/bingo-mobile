import { useEffect, useMemo, useState } from 'react';

/** Longest countdown the server ever announces, in seconds. */
export const MAX_COUNTDOWN_SECONDS = 30;
/** Used when the announced start time is missing or the clocks disagree. */
export const DEFAULT_COUNTDOWN_SECONDS = 5;
export const DEFAULT_CLAIM_WINDOW_SECONDS = 10;

function seedSeconds(targetIso: string | null | undefined, max = MAX_COUNTDOWN_SECONDS, def = DEFAULT_COUNTDOWN_SECONDS) {
  const target = targetIso ? new Date(targetIso).getTime() : Number.NaN;
  const left = Number.isFinite(target) ? Math.ceil((target - Date.now()) / 1000) : Number.NaN;
  return Number.isFinite(left) && left > 0 && left <= max ? left : def;
}

/**
 * Seconds left until a game that is counting down actually starts.
 *
 * Kept here rather than in the screen so the first call of a game, a resume, a
 * restart and a round that continues after a claim all tick down the same way.
 */
/** Seconds left until a deadline (claim window or start). */
export function useCountdownTo(targetIso: string | null | undefined, active: boolean, max = MAX_COUNTDOWN_SECONDS, def = DEFAULT_COUNTDOWN_SECONDS) {
  const countdownKey = `${active}|${targetIso ?? ''}|${max}|${def}`;
  const [seenKey, setSeenKey] = useState(countdownKey);
  const [tick, setTick] = useState(0);

  if (countdownKey !== seenKey) {
    setSeenKey(countdownKey);
    setTick(0);
  }

  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => setTick((prev) => prev + 1), 1000);
    return () => clearInterval(id);
  }, [active]);

  return useMemo(() => {
    if (!active) return 0;
    return Math.max(0, seedSeconds(targetIso, max, def) - tick);
  }, [active, targetIso, tick, max, def]);
}

export const MAX_CLAIM_WINDOW_SECONDS = 30;

export function useCountdown(targetIso: string | null | undefined, active: boolean) {
  return useCountdownTo(targetIso, active);
}
