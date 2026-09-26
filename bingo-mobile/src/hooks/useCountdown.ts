import { useEffect, useState } from 'react';

export function useCountdown(seconds: number | null, onEnd?: () => void) {
  const [remaining, setRemaining] = useState<number | null>(seconds);

  useEffect(() => {
    if (seconds == null || seconds <= 0) return undefined;
    let local = seconds;
    const id = setInterval(() => {
      local -= 1;
      setRemaining(local);
      if (local <= 0) {
        clearInterval(id);
        onEnd?.();
      }
    }, 1000);
    return () => clearInterval(id);
  }, [seconds, onEnd]);

  return remaining;
}