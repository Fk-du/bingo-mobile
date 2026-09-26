import { Text, View } from 'react-native';
import { useTranslate } from '@/hooks/useTranslate';

const RANGES = [
  { letter: 'B', min: 1, max: 15 },
  { letter: 'I', min: 16, max: 30 },
  { letter: 'N', min: 31, max: 45 },
  { letter: 'G', min: 46, max: 60 },
  { letter: 'O', min: 61, max: 75 },
] as const;

const TONES = [
  { chip: 'border-cyan-500/40 bg-cyan-400/15 text-cyan-500', called: 'border-cyan-600 bg-cyan-600' },
  { chip: 'border-emerald-500/40 bg-emerald-400/15 text-emerald-500', called: 'border-emerald-600 bg-emerald-600' },
  { chip: 'border-blue-500/40 bg-blue-400/15 text-blue-500', called: 'border-blue-600 bg-blue-600' },
  { chip: 'border-amber-500/40 bg-amber-400/15 text-amber-500', called: 'border-amber-600 bg-amber-600' },
  { chip: 'border-rose-500/40 bg-rose-400/15 text-rose-500', called: 'border-rose-600 bg-rose-600' },
];

export function NumberBoard({
  calledNumbers,
  lastCalledNumber = null,
}: {
  calledNumbers: number[];
  lastCalledNumber?: number | null;
}): React.JSX.Element {
  const t = useTranslate();
  const called = new Set(calledNumbers);

  const lastRange = RANGES.find((r) => lastCalledNumber != null && lastCalledNumber >= r.min && lastCalledNumber <= r.max);
  const lastLetter = lastRange ? lastRange.letter : '';

  return (
    <View className="w-full rounded-2xl border border-bp-borderActive40 bg-bp-surface px-2.5 pb-2 pt-2">
      <View className="flex-row items-center justify-between px-0.5 pb-2">
        <View className="flex-row items-center" style={{ gap: 6 }}>
          <Text className="text-[10px] font-bold uppercase tracking-[0.18em] text-bp-textSecondary">
            {t('game.calledNumbers') ?? 'Called numbers'}
          </Text>
          <View className="rounded-full border border-bp-gold30 bg-bp-gold10 px-2 py-0.5">
            <Text className="text-[10px] font-black text-bp-goldInk">{calledNumbers.length}/75</Text>
          </View>
        </View>
        {lastCalledNumber != null && (
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
      </View>

      <View style={{ gap: 4 }}>
        {RANGES.map(({ letter, min, max }, toneIdx) => {
          const tone = TONES[toneIdx];
          return (
            <View key={letter} className="flex-row items-stretch" style={{ gap: 3 }}>
              <View
                className={`w-7 items-center justify-center rounded-lg border font-bold ${tone.chip}`}
                style={{ height: 24 }}
              >
                <Text className="text-[11px] font-black">{letter}</Text>
              </View>
              {Array.from({ length: max - min + 1 }, (_, i) => min + i).map((n) => {
                const isCalled = called.has(n);
                const isLast = n === lastCalledNumber;
                return (
                  <View
                    key={n}
                    className={`flex-1 items-center justify-center rounded-md border ${
                      isLast
                        ? 'border-amber-300/90 bg-amber-400'
                        : isCalled
                          ? tone.called
                          : 'border-bp-borderInactive bg-bp-surfaceAlt'
                    }`}
                    style={{
                      height: 24,
                      ...(isLast ? { boxShadow: '0 0 10px rgba(242,201,76,0.55)' } : {}),
                    }}
                  >
                    <Text
                      className={`text-[9px] tracking-tight ${
                        isLast ? 'text-black font-black' : isCalled ? 'text-white font-bold' : 'text-bp-textSecondary'
                      }`}
                    >
                      {n}
                    </Text>
                  </View>
                );
              })}
            </View>
          );
        })}
      </View>
    </View>
  );
}