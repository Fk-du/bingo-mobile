import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import type { TextStyle } from 'react-native';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';

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
const CHIP_TONES = [
  { border: '#0891b2', bg: '#0891b215', text: '#0891b2' },
  { border: '#059669', bg: '#05966915', text: '#059669' },
  { border: '#2563eb', bg: '#2563eb15', text: '#2563eb' },
  { border: '#8B5E3C', bg: '#8B5E3C15', text: '#8B5E3C' },
  { border: '#e11d48', bg: '#e11d4815', text: '#e11d48' },
];

const LAST_BORDER = 'rgba(139, 94, 60, 0.9)';
const LAST_BACKGROUND = '#8B5E3C';

/** The B/I/N/G/O column letter a number falls in, for compact call labels. */
export function numberLetter(n: number): string {
  return RANGES.find((r) => n >= r.min && n <= r.max)?.letter ?? '';
}

export function NumberBoard({
  calledNumbers,
  lastCalledNumber = null,
  registeredCount = 0,
}: {
  calledNumbers: number[];
  lastCalledNumber?: number | null;
  registeredCount?: number;
}): React.JSX.Element {
  const t = useTranslate();
  const { colors } = useTheme();
  const [collapsed, setCollapsed] = useState(false);
  const recentRef = useRef<ScrollView>(null);
  // Whether the trail is resting at the newest chip, so arrivals keep it in
  // view without yanking the player back while they read older numbers.
  const stickToEnd = useRef(true);
  const called = new Set(calledNumbers);

  const lastLetter = lastCalledNumber != null ? numberLetter(lastCalledNumber) : '';
  const toggleLabel = collapsed
    ? (t('game.expandNumbers') ?? 'Expand called numbers')
    : (t('game.minimizeNumbers') ?? 'Minimize called numbers');

  useEffect(() => {
    if (collapsed && stickToEnd.current) recentRef.current?.scrollToEnd({ animated: false });
  }, [collapsed, calledNumbers.length]);

  return (
    <View className="w-full rounded-2xl border px-2.5 pb-2 pt-2" style={{ borderColor: colors.borderActive + '40', backgroundColor: colors.surface }}>
      <View className="flex-row items-center justify-between px-0.5" style={{ gap: 6 }}>
        <View className="min-w-0 flex-1 flex-row items-center" style={{ gap: 6 }}>
          {!collapsed && (
            <Text className="text-[10px] font-bold uppercase tracking-[0.18em]" style={{ color: colors.textSecondary }}>
              {t('game.calledNumbers') ?? 'Called numbers'}
            </Text>
          )}
          <View className="rounded-full border px-2 py-0.5" style={{ borderColor: '#8B5E3C30', backgroundColor: '#8B5E3C10' }}>
            <Text className="text-[10px] font-black" style={{ color: colors.gold }}>{calledNumbers.length}/75</Text>
          </View>
          {registeredCount > 0 && (
            <View className="rounded-full border px-2 py-0.5" style={{ borderColor: '#6B5BFF30', backgroundColor: '#6B5BFF10' }}>
              <Text className="text-[10px] font-black" style={{ color: colors.primary }}>
                {t('game.cardsRegistered', { count: String(registeredCount) })}
              </Text>
            </View>
          )}
          {collapsed && calledNumbers.length > 0 && (
            <ScrollView
              ref={recentRef}
              horizontal
              showsHorizontalScrollIndicator={false}
              style={{ flex: 1, minWidth: 0 }}
              contentContainerStyle={{ flexDirection: 'row', alignItems: 'center', gap: 3 }}
              scrollEventThrottle={32}
              onScroll={(e) => {
                const { contentOffset, contentSize, layoutMeasurement } = e.nativeEvent;
                stickToEnd.current = contentOffset.x >= contentSize.width - layoutMeasurement.width - 8;
              }}
            >
              <RecentCalls numbers={calledNumbers} />
            </ScrollView>
          )}
        </View>
        <View className="flex-row items-center" style={{ gap: 6 }}>
          {!collapsed && lastCalledNumber != null && (
            <View className="flex-row items-center" style={{ gap: 5 }}>
              <Text className="text-[9px] font-bold uppercase tracking-wider" style={{ color: colors.textSecondary }}>
                {t('game.lastCalled') ?? 'Last'}
              </Text>
              <View
                className="rounded-lg border px-2 py-0.5"
                style={{ borderColor: '#fbbf2480', backgroundColor: '#fbbf24', boxShadow: '0 0 12px rgba(242,201,76,0.6)' }}
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
            className="h-6 w-6 items-center justify-center rounded-full border active:opacity-70"
            style={{ borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt }}
          >
            <Text className="text-[11px] leading-none" style={{ color: colors.textSecondary }}>
              {collapsed ? '▴' : '▾'}
            </Text>
          </Pressable>
        </View>
      </View>

      {!collapsed ? (
        <View style={{ gap: 4 }}>
          {RANGES.map(({ letter, min, max }, toneIdx) => {
            const tone = CHIP_TONES[toneIdx];
            return (
              <View key={letter} className="flex-row items-stretch" style={{ gap: 3 }}>
                <View
                  className="w-7 items-center justify-center rounded-lg border font-bold"
                  style={{ height: 26, borderColor: tone.border + '40', backgroundColor: tone.bg }}
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
                        boxShadow: '0 0 10px rgba(139,94,60,0.55)',
                      }
                    : isCalled
                      ? { borderColor: tone.border, backgroundColor: tone.bg }
                      : { borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt };
                  const inkStyle: TextStyle | undefined = isLast
                    ? { color: '#000000', fontWeight: '900' }
                    : isCalled
                      ? { color: '#ffffff', fontWeight: '700' }
                      : { color: colors.textSecondary };
                  return (
                    <View
                      key={n}
                      className="flex-1 items-center justify-center rounded-md border"
                      style={{ height: 26, ...cellStyle }}
                    >
                      <Text className="text-[11px] font-bold tracking-tight" style={inkStyle}>
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
 * The minimized board's inline trail of calls, newest on the right. The whole
 * history is shown rather than a slice: the row scrolls sideways, so a player
 * can read back through every number that has come out, and the latest chip
 * stays highlighted as the marker for what just came.
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