import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useEffect, useRef, useState } from 'react';
import * as Speech from 'expo-speech';

const STORAGE_KEY = 'bingo-call-sound-muted';

function letterFor(n: number): string {
  if (n >= 1 && n <= 15) return 'B';
  if (n >= 16 && n <= 30) return 'I';
  if (n >= 31 && n <= 45) return 'N';
  if (n >= 46 && n <= 60) return 'G';
  if (n >= 61 && n <= 75) return 'O';
  return '';
}

export function useNumberAnnouncer(numbers: number[]) {
  const [muted, setMuted] = useState(false);
  const mutedRef = useRef(false);
  const prevLen = useRef<number | null>(null);
  const numbersRef = useRef<number[]>(numbers);

  useEffect(() => {
    numbersRef.current = numbers;
  }, [numbers]);

  useEffect(() => {
    let cancelled = false;
    void AsyncStorage.getItem(STORAGE_KEY).then((stored) => {
      if (cancelled) return;
      const isMuted = stored === '1';
      mutedRef.current = isMuted;
      setMuted(isMuted);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (mutedRef.current) return;
    const len = numbers.length;
    const prev = prevLen.current;
    prevLen.current = len;
    if (prev === null || len <= prev || len === 0) return;
    void Speech.speak(`${letterFor(numbers[len - 1])} ${numbers[len - 1]}`, {
      language: 'en',
      rate: 0.85,
    });
  }, [numbers]);

  const toggleMuted = useCallback(() => {
    setMuted((m) => {
      const next = !m;
      mutedRef.current = next;
      void AsyncStorage.setItem(STORAGE_KEY, next ? '1' : '0');
      Speech.stop();
      return next;
    });
  }, []);

  return { muted, toggleMuted };
}