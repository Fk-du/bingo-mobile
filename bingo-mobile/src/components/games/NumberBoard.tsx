import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, ScrollView, Text, View } from 'react-native';
import type { ViewStyle } from 'react-native';
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

// The last-called ball stands out from the rest: gold instead of the column
// colour, with dark ink in the white disc so it reads against the bright ball.
const LAST_COLOR = '#FFB454';
const LAST_INK = '#241a00';
const LAST_GLOW = '0 0 14px rgba(255, 180, 84, 0.85)';

/** The B/I/N/G/O column letter a number falls in, for compact call labels. */
export function numberLetter(n: number): string {
  return RANGES.find((r) => n >= r.min && n <= r.max)?.letter ?? '';
}

function toneFor(n: number): (typeof CHIP_TONES)[number] {
  return CHIP_TONES[RANGES.findIndex((r) => n >= r.min && n <= r.max) % CHIP_TONES.length] ?? CHIP_TONES[0];
}

/**
 * A single pool-ball call marker: a round ball in the range colour with a white
 * number disc, the way billiard balls show their number. The most recent call
 * (`bounce`) turns gold and pulses on the spot so the new number catches the
 * eye the moment it lands.
 */
function PoolBall({
  n,
  letter,
  color,
  ink,
  size,
  bounce = false,
  bare = false,
  borderColor = 'transparent',
}: {
  n: number;
  letter?: string;
  color: string;
  ink: string;
  size?: number;
  bounce?: boolean;
  bare?: boolean;
  borderColor?: string;
}): React.JSX.Element {
  const [pulse] = useState(() => new Animated.Value(0));

  useEffect(() => {
    if (!bounce) return;
    const anim = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1, duration: 260, easing: Easing.out(Easing.quad), useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 0, duration: 260, easing: Easing.in(Easing.quad), useNativeDriver: true }),
      ])
    );
    anim.start();
    return () => anim.stop();
  }, [bounce, pulse]);

  const outerStyle: ViewStyle = size
    ? { width: size, height: size, borderRadius: size / 2 }
    : { width: '100%', aspectRatio: 1, borderRadius: 999, maxWidth: 34 };
  const innerSize = size ? Math.round(size * 0.62) : undefined;
  const fontSize = size ? Math.max(10, Math.round(size * 0.3)) : 11;

  return (
    <Animated.View
      className="items-center justify-center"
      style={[
        outerStyle,
        {
          backgroundColor: color,
          borderColor: bounce ? '#FFF7E0' : borderColor,
          borderWidth: bare ? 1 : bounce ? 2 : 0,
          boxShadow: bounce ? LAST_GLOW : undefined,
          transform: [{ scale: pulse.interpolate({ inputRange: [0, 1], outputRange: [1, 1.16] }) }],
        },
      ]}
    >
      <View
        style={{
          width: innerSize ?? '58%',
          height: innerSize,
          aspectRatio: innerSize ? undefined : 1,
          borderRadius: innerSize ? innerSize / 2 : 999,
          backgroundColor: bare ? 'rgba(255,255,255,0.45)' : 'rgba(255,255,255,0.92)',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Text className="font-black" style={{ fontSize, color: ink, lineHeight: fontSize * 1.15, letterSpacing: -0.4 }}>
          {letter != null ? `${letter} ${n}` : n}
        </Text>
      </View>
      <View
        pointerEvents="none"
        style={{
          position: 'absolute',
          top: size ? size * 0.12 : '11%',
          left: size ? size * 0.18 : '16%',
          width: size ? size * 0.3 : '28%',
          height: size ? size * 0.14 : '12%',
          borderRadius: size ? size * 0.07 : 40,
          backgroundColor: bare ? 'transparent' : 'rgba(255,255,255,0.3)',
        }}
      />
    </Animated.View>
  );
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
  // The trail is newest-first, so it rests at the newest chip. Whether it is at
  // that start edge decides whether an arrival keeps it in view without yanking
  // the player back while they read older numbers.
  const stickToStart = useRef(true);
  const called = new Set(calledNumbers);

  const toggleLabel = collapsed
    ? (t('game.expandNumbers') ?? 'Expand called numbers')
    : (t('game.minimizeNumbers') ?? 'Minimize called numbers');

  useEffect(() => {
    if (collapsed && stickToStart.current) recentRef.current?.scrollTo({ x: 0, animated: false });
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
              contentContainerStyle={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}
              scrollEventThrottle={32}
              onScroll={(e) => {
                stickToStart.current = e.nativeEvent.contentOffset.x <= 8;
              }}
            >
              <RecentCalls numbers={calledNumbers} />
            </ScrollView>
          )}
        </View>
        <View className="flex-row items-center" style={{ gap: 6 }}>
          {!collapsed && lastCalledNumber != null && (
            <PoolBall n={lastCalledNumber} color={LAST_COLOR} ink={LAST_INK} size={36} bounce />
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
        <View className="mt-1" style={{ gap: 4 }}>
          {RANGES.map(({ letter, min, max }, toneIdx) => {
            const tone = CHIP_TONES[toneIdx];
            return (
              <View key={letter} className="flex-row items-stretch" style={{ gap: 3 }}>
                <View
                  className="w-7 items-center justify-center rounded-lg border font-bold"
                  style={{ height: 34, borderColor: tone.border + '40', backgroundColor: tone.bg }}
                >
                  <Text className="text-[11px] font-black" style={{ color: tone.text }}>{letter}</Text>
                </View>
                {Array.from({ length: max - min + 1 }, (_, i) => min + i).map((n) => {
                  const isCalled = called.has(n);
                  const isLast = n === lastCalledNumber;
                  return (
                    <View
                      key={n}
                      className="flex-1 items-center justify-center"
                      style={{ height: 34 }}
                    >
                      {isLast ? (
                        <PoolBall n={n} color={LAST_COLOR} ink={LAST_INK} bounce />
                      ) : isCalled ? (
                        <PoolBall n={n} color={tone.text} ink={tone.text} />
                      ) : (
                        <PoolBall n={n} color={colors.surfaceAlt} ink={colors.textSecondary} bare borderColor={colors.borderInactive} />
                      )}
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
 * The minimized board's inline trail of calls, newest on the left. The whole
 * history is shown rather than a slice: the row scrolls sideways, so a player
 * can read back through every number that has come out, and the latest chip
 * stays at the resting edge so a minimized board always shows the newest call.
 * Every chip is a small pool ball in its range colour; the newest bounces gold.
 */
function RecentCalls({ numbers }: { numbers: number[] }): React.JSX.Element {
  return (
    <>
      {[...numbers].reverse().map((n, i) => {
        const isLatest = i === 0;
        const tone = toneFor(n);
        return isLatest ? (
          <PoolBall key={`${n}-${i}`} n={n} color={LAST_COLOR} ink={LAST_INK} size={34} bounce />
        ) : (
          <PoolBall key={`${n}-${i}`} n={n} color={tone.text} ink={tone.text} size={28} />
        );
      })}
    </>
  );
}