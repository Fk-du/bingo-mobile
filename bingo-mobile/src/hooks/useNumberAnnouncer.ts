import { useEffect, useRef } from 'react';
import * as Speech from 'expo-speech';
import { useGameSettings } from '@/store/gameSettings.store';

function letterFor(n: number): string {
  if (n >= 1 && n <= 15) return 'B';
  if (n >= 16 && n <= 30) return 'I';
  if (n >= 31 && n <= 45) return 'N';
  if (n >= 46 && n <= 60) return 'G';
  if (n >= 61 && n <= 75) return 'O';
  return '';
}

/**
 * Speaks each number as it is called, unless the player has turned the sound
 * off. Whether it is on is a general preference in the settings store rather
 * than a per-game switch on the board: the same choice then applies to the
 * next game without being made again, which is why nothing is returned here
 * and the board no longer carries a sound control.
 */
export function useNumberAnnouncer(numbers: number[]) {
  const soundEnabled = useGameSettings((s) => s.soundEnabled);
  const enabledRef = useRef(soundEnabled);
  const prevLen = useRef<number | null>(null);
  const numbersRef = useRef<number[]>(numbers);

  useEffect(() => {
    numbersRef.current = numbers;
  }, [numbers]);

  useEffect(() => {
    enabledRef.current = soundEnabled;
    if (!soundEnabled) Speech.stop();
  }, [soundEnabled]);

  useEffect(() => {
    if (!enabledRef.current) return;
    const len = numbers.length;
    const prev = prevLen.current;
    prevLen.current = len;
    if (prev === null || len <= prev || len === 0) return;
    void Speech.speak(`${letterFor(numbers[len - 1])} ${numbers[len - 1]}`, {
      language: 'en',
      rate: 0.85,
    });
  }, [numbers]);
}