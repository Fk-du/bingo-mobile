import { useEffect, useMemo, useState } from 'react';

/** Longest countdown the server ever announces, in seconds. */
export const MAX_COUNTDOWN_SECONDS = 15;
/** Used when the announced start time is missing or the clocks disagree. */
export const DEFAULT_COUNTDOWN_SECONDS = 5;

function seedSeconds(targetIso: string | null | undefined) {
  const target = targetIso ? new Date(targetIso).getTime() : Number.NaN;
  const left = Number.isFinite(target) ? Math.ceil((target - Date.now()) / 1000) : Number.NaN;
  // The server sends its start time without a timezone, so the value is only
  // trusted while it is plausible; otherwise count down locally instead.
  return Number.isFinite(left) && left > 0 && left <= MAX_COUNTDOWN_SECONDS
    ? left
    : DEFAULT_COUNTDOWN_SECONDS;
}

/**
 * Seconds left until a game that is counting down actually starts.
 *
 * Kept here rather than in the screen so the first call of a game, a resume, a
 * restart and a round that continues after a claim all tick down the same way.
 */
export function useCountdown(targetIso: string | null | undefined, active: boolean) {
  const countdownKey = `${active}|${targetIso ?? ''}`;
  const [seenKey, setSeenKey] = useState(countdownKey);
  const [tick, setTick] = useState(0);

  // A new countdown starts from its announced length, so drop the elapsed ticks
  // whenever the game announces a different one.
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
    return Math.max(0, seedSeconds(targetIso) - tick);
  }, [active, targetIso, tick]);
}
