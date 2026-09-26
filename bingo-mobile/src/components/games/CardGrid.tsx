import { Pressable, Text, View } from 'react-native';

const LETTERS = ['B', 'I', 'N', 'G', 'O'];
const LETTER_COLORS = [
  'bg-cyan-400/20 border-cyan-400/40 text-cyan-500',
  'bg-emerald-400/20 border-emerald-400/40 text-emerald-500',
  'bg-amber-400/20 border-amber-400/40 text-amber-500',
  'bg-bp-gold20 border-bp-gold50 text-bp-goldInk',
  'bg-rose-400/20 border-rose-400/40 text-rose-500',
];

interface CardGridProps {
  numbers: number[][];
  called?: number[];
  marked?: number[];
  lastCalledNumber?: number | null;
  interactive?: boolean;
  onToggle?: (n: number) => void;
}

export function CardGrid({
  numbers,
  called = [],
  marked = [],
  lastCalledNumber,
  interactive = false,
  onToggle,
}: CardGridProps) {
  const calledSet = new Set(called);
  const markedSet = new Set(marked);
  const rows = numbers.slice(0, 5);

  return (
    <View
      className="w-full rounded-xl border border-bp-border bg-bp-background p-1.5"
      style={{ gap: 2 }}
    >
      <View className="flex-row" style={{ gap: 2 }}>
        {LETTERS.map((letter, index) => (
          <View
            key={letter}
            className={`flex-1 aspect-square items-center justify-center rounded border font-bold ${LETTER_COLORS[index]}`}
          >
            <Text className="font-bold text-[10px]">{letter}</Text>
          </View>
        ))}
      </View>
      {rows.map((row, r) => (
        <View key={r} className="flex-row" style={{ gap: 2 }}>
          {row.map((n, c) => {
            const isFree = r === 2 && c === 2;
            const isLast = !isFree && lastCalledNumber != null && n === lastCalledNumber;
            const isDaubed = isFree ? false : markedSet.has(n) || calledSet.has(n);
            const display = isFree ? '★' : n;
            return (
              <Pressable
                key={`${r}-${c}`}
                disabled={!interactive}
                onPress={() => interactive && onToggle?.(n)}
                className={`flex-1 aspect-square items-center justify-center rounded border ${
                  isFree
                    ? 'bg-bp-gold border-bp-gold'
                    : isLast
                      ? 'bg-bp-gold border-bp-gold shadow-lg'
                      : isDaubed
                        ? 'bg-bp-danger border-bp-danger60'
                        : 'bg-bp-surface border-bp-borderInactive'
                }`}
              >
                <Text
                  className={`font-semibold text-[10px] ${
                    isFree || isLast
                      ? 'text-[#241a00]'
                      : isDaubed
                        ? 'text-white'
                        : 'text-bp-textSecondary'
                  }`}
                >
                  {display}
                </Text>
              </Pressable>
            );
          })}
        </View>
      ))}
    </View>
  );
}