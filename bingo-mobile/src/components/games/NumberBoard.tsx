import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import type { TextStyle } from 'react-native';
import { useTranslate } from '@/hooks/useTranslate';

const RANGES = [
  { letter: 'B', min: 1, max: 15 },
  { letter: 'I', min: 16, max: 30 },
  { letter: 'N', min: 31, max: 45 },
  { letter: 'G', min: 46, max: 60 },
  { letter: 'O', min: 61, max: 75 },
] as const;

// `chip` is constant per column, so it is safe to keep in className. The called
// state is expressed as literal colours instead of a swapped className: see the
// note in CardGrid for why a className change here crashes the app.
const TONES = [
  { chip: 'border-cyan-500/40 bg-cyan-400/15 text-cyan-500', calledBorder: '#0891b2', calledBackground: '#0891b2' },
  { chip: 'border-emerald-500/40 bg-emerald-400/15 text-emerald-500', calledBorder: '#059669', calledBackground: '#059669' },
  { chip: 'border-blue-500/40 bg-blue-400/15 text-blue-500', calledBorder: '#2563eb', calledBackground: '#2563eb' },
  { chip: 'border-amber-500/40 bg-amber-400/15 text-amber-500', calledBorder: '#d97706', calledBackground: '#d97706' },
  { chip: 'border-rose-500/40 bg-rose-400/15 text-rose-500', calledBorder: '#e11d48', calledBackground: '#e11d48' },
];

const LAST_BORDER = 'rgba(252, 211, 77, 0.9)';
const LAST_BACKGROUND = '#fbbf24';

// How many recent calls the minimized one-liner keeps in view.
const RECENT_LIMIT = 5;

export function NumberBoard({
  calledNumbers,
  lastCalledNumber = null,
  registeredCount = 0,
}: {
  calledNumbers: number[];
  lastCalledNumber?: number | null;
  /** Registered cards held by this player, shown next to the called count. */
  registeredCount?: number;
}): React.JSX.Element {
  const t = useTranslate();
  const [collapsed, setCollapsed] = useState(false);
  const called = new Set(calledNumbers);

  const lastRange = RANGES.find((r) => lastCalledNumber != null && lastCalledNumber >= r.min && lastCalledNumber <= r.max);
  const lastLetter = lastRange ? lastRange.letter : '';
  // Newest last: the rightmost ball is the most recent call.
  const recent = calledNumbers.slice(-RECENT_LIMIT);
  const toggleLabel = collapsed
    ? (t('game.expandNumbers') ?? 'Expand called numbers')
    : (t('game.minimizeNumbers') ?? 'Minimize called numbers');

  return (
    <View className="w-full rounded-2xl border border-bp-borderActive40 bg-bp-surface px-2.5 pb-2 pt-2">
      {/* One row either way. Minimized used to stack a header above the recent
          balls, which cost two rows of height to say very little; now the
          balls sit inline with the counts and the toggle. */}
      <View className="flex-row items-center justify-between px-0.5" style={{ gap: 6 }}>
        <View className="min-w-0 flex-1 flex-row items-center" style={{ gap: 6 }}>
          {!collapsed && (
            <Text className="text-[10px] font-bold uppercase tracking-[0.18em] text-bp-textSecondary">
              {t('game.calledNumbers') ?? 'Called numbers'}
            </Text>
          )}
          <View className="rounded-full border border-bp-gold30 bg-bp-gold10 px-2 py-0.5">
            <Text className="text-[10px] font-black text-bp-goldInk">{calledNumbers.length}/75</Text>
          </View>
          {registeredCount > 0 && (
            <View className="rounded-full border border-bp-primary40 bg-bp-primary10 px-2 py-0.5">
              <Text className="text-[10px] font-black text-bp-primary">
                {t('game.cardsRegistered', { count: String(registeredCount) })}
              </Text>
            </View>
          )}
          {collapsed && recent.length > 0 && (
            <View className="min-w-0 flex-1 flex-row items-center" style={{ gap: 3 }}>
              <RecentCalls numbers={recent} />
            </View>
          )}
        </View>
        <View className="flex-row items-center" style={{ gap: 6 }}>
          {!collapsed && lastCalledNumber != null && (
            <View className="flex-row items-center" style={{ gap: 5 }}>
              <Text className="text-[9px] font-bold uppercase tracking-wider text-bp-textSecondary">
                {t('game.lastCalled') ?? 'Last'}
              </Text>
              <View
                className="rounded-lg border border-amber-300/80 bg-amber-400 px-2 py-0.5"
                style={{ boxShadow: '0 0 12px rgba(242,201,76,0.6)' }}
              >
                <Text className="text-[11px] font-black text-black">
                  {lastLetter} {lastCalledNumber}
                </Text>
              </View>
            </View>
          )}
          <Pressable
            onPress={() => setCollapsed((v) => !v)}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityState={{ expanded: !collapsed }}
            accessibilityLabel={toggleLabel}
            className="h-6 w-6 items-center justify-center rounded-full border border-bp-borderInactive bg-bp-surfaceAlt active:opacity-70"
          >
            <Text className="text-[11px] leading-none text-bp-textSecondary">
              {collapsed ? '▴' : '▾'}
            </Text>
          </Pressable>
        </View>
      </View>

      {!collapsed ? (
        <View style={{ gap: 4 }}>
          {RANGES.map(({ letter, min, max }, toneIdx) => {
            const tone = TONES[toneIdx];
            return (
              <View key={letter} className="flex-row items-stretch" style={{ gap: 3 }}>
                <View
                  className={`w-7 items-center justify-center rounded-lg border font-bold ${tone.chip}`}
                  style={{ height: 26 }}
                >
                  <Text className="text-[11px] font-black">{letter}</Text>
                </View>
                {Array.from({ length: max - min + 1 }, (_, i) => min + i).map((n) => {
                  const isCalled = called.has(n);
                  const isLast = n === lastCalledNumber;
                  const cellStyle = isLast
                    ? {
                        borderColor: LAST_BORDER,
                        backgroundColor: LAST_BACKGROUND,
                        boxShadow: '0 0 10px rgba(242,201,76,0.55)',
                      }
                    : isCalled
                      ? { borderColor: tone.calledBorder, backgroundColor: tone.calledBackground }
                      : undefined;
                  const inkStyle: TextStyle | undefined = isLast
                    ? { color: '#000000', fontWeight: '900' }
                    : isCalled
                      ? { color: '#ffffff', fontWeight: '700' }
                      : undefined;
                  return (
                    <View
                      key={n}
                      className="flex-1 items-center justify-center rounded-md border border-bp-borderInactive bg-bp-surfaceAlt"
                      style={{ height: 26, ...cellStyle }}
                    >
                      <Text className="text-[11px] font-bold tracking-tight text-bp-textSecondary" style={inkStyle}>
                        {n}
                      </Text>
                    </View>
                  );
                })}
              </View>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}

/**
 * The minimized board's inline trail of recent calls, newest on the right.
 * These are chips rather than the big circles the expanded grid uses: when the
 * board is collapsed the only job is "what came out recently", and a row of
 * small chips fits beside the counts without stealing a whole row of height.
 */
function RecentCalls({ numbers }: { numbers: number[] }): React.JSX.Element {
  return (
    <>
      {numbers.map((n, i) => {
        const isLatest = i === numbers.length - 1;
        return (
          <View
            key={`${n}-${i}`}
            className="items-center justify-center rounded-md border"
            style={
              isLatest
                ? {
                    minWidth: 26,
                    height: 20,
                    paddingHorizontal: 5,
                    borderColor: LAST_BORDER,
                    backgroundColor: LAST_BACKGROUND,
                  }
                : {
                    minWidth: 20,
                    height: 20,
                    paddingHorizontal: 3,
                    borderColor: 'rgba(148,163,184,0.25)',
                    backgroundColor: 'transparent',
                  }
            }
          >
            <Text
              style={
                isLatest
                  ? { fontSize: 11, fontWeight: '900', color: '#000000' }
                  : { fontSize: 10, fontWeight: '700', color: '#94a3b8' }
              }
            >
              {n}
            </Text>
          </View>
        );
      })}
    </>
  );
}