import { Pressable, Text, View } from 'react-native';

const LETTERS = ['B', 'I', 'N', 'G', 'O'];
const LETTER_COLORS = [
  'bg-cyan-400/20 border-cyan-400/40 text-cyan-500',
  'bg-emerald-400/20 border-emerald-400/40 text-emerald-500',
  'bg-amber-400/20 border-amber-400/40 text-amber-500',
  'bg-bp-gold20 border-bp-gold50 text-bp-goldInk',
  'bg-rose-400/20 border-rose-400/40 text-rose-500',
];

// State colours live here rather than in swapped classNames. Tailwind compiles
// shadow-lg to a `--tw-shadow` variable and every `bp-*` colour to another
// variable, so a cell whose className changed when a number was called made
// NativeWind try to "upgrade" the component onto a variable it never declared.
// That upgrade path throws while stringifying props and takes the app down the
// first time a number is called. Inline styles skip the CSS interop entirely.
const GOLD = '#F2C94C';
const LAST_INK = '#241a00';
const DEFAULT_MARK = { fill: '#FF5C6C', border: '#FF5C6C99' };

interface CardGridProps {
  numbers: number[][];
  called?: number[];
  marked?: number[];
  lastCalledNumber?: number | null;
  interactive?: boolean;
  onToggle?: (n: number) => void;
  /** Multi-select mode: taps toggle selection instead of marking. */
  selectable?: boolean;
  selected?: boolean;
  onSelect?: () => void;
  onLongPressCard?: () => void;
  /** Fill for a called/marked cell. Chosen by the player in game settings. */
  markColor?: { fill: string; border: string };
}

export function CardGrid({
  numbers,
  called = [],
  marked = [],
  lastCalledNumber,
  interactive = false,
  onToggle,
  selectable = false,
  selected = false,
  onSelect,
  onLongPressCard,
  markColor = DEFAULT_MARK,
}: CardGridProps) {
  const calledSet = new Set(called);
  const markedSet = new Set(marked);
  const rows = numbers.slice(0, 5);

  return (
    <View
      className="w-full rounded-xl border border-bp-border bg-bp-background p-1.5"
      style={{ gap: 2, ...(selected ? { borderColor: '#6B5BFF' } : {}) }}
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
            const display = isFree ? 'F' : n;
            const cellStyle = isFree
              ? { backgroundColor: GOLD, borderColor: GOLD }
              : isLast
                ? { backgroundColor: GOLD, borderColor: GOLD, boxShadow: '0 6px 14px rgba(0,0,0,0.35)' }
                : isDaubed
                  ? { backgroundColor: markColor.fill, borderColor: markColor.border }
                  : undefined;
            const inkStyle =
              isFree || isLast
                ? { color: LAST_INK }
                : isDaubed
                  ? { color: '#ffffff' }
                  : undefined;
            return (
              <Pressable
                key={`${r}-${c}`}
                disabled={!selectable && !interactive && !onLongPressCard}
                onPress={() => (selectable ? onSelect?.() : interactive && onToggle?.(n))}
                onLongPress={onLongPressCard}
                delayLongPress={350}
                className="flex-1 aspect-square items-center justify-center rounded border bg-bp-surface border-bp-borderInactive"
                style={cellStyle}
              >
                <Text className="font-black text-[11px] text-bp-textSecondary" style={inkStyle}>
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