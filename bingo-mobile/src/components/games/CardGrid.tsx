import { Pressable, Text, View } from 'react-native';
import { useTheme } from '@/lib/theme';

const LETTERS = ['B', 'I', 'N', 'G', 'O'];
const LETTER_BORDERS = ['#0891b2', '#059669', '#d97706', '#8B5E3C', '#e11d48'];
const LETTER_BACKGROUNDS = ['#0891b215', '#05966915', '#d9770615', '#8B5E3C15', '#e11d4815'];
const LETTER_INKS = ['#0891b2', '#059669', '#d97706', '#b8860b', '#e11d48'];

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
  const { colors } = useTheme();
  const calledSet = new Set(called);
  const markedSet = new Set(marked);
  const rows = numbers.slice(0, 5);

  return (
    <View
      className="w-full rounded-xl border p-1.5"
      style={{ gap: 2, borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt, ...(selected ? { borderColor: colors.primary } : {}) }}
    >
      <View className="flex-row" style={{ gap: 2 }}>
        {LETTERS.map((letter, index) => (
          <View
            key={letter}
            className="flex-1 aspect-square items-center justify-center rounded border font-bold"
            style={{
              borderColor: LETTER_BORDERS[index],
              backgroundColor: LETTER_BACKGROUNDS[index],
            }}
          >
            <Text className="font-bold text-[12px]" style={{ color: LETTER_INKS[index] }}>{letter}</Text>
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
            const defaultCellStyle = { borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt };
            const defaultInk = { color: colors.textSecondary };
            const cellStyle = isFree
              ? { backgroundColor: GOLD, borderColor: GOLD }
              : isLast
                ? { backgroundColor: GOLD, borderColor: GOLD, boxShadow: '0 6px 14px rgba(0,0,0,0.35)' }
                : isDaubed
                  ? { backgroundColor: markColor.fill, borderColor: markColor.border }
                  : defaultCellStyle;
            const inkStyle =
              isFree || isLast
                ? { color: LAST_INK }
                : isDaubed
                  ? { color: '#ffffff' }
                  : defaultInk;
            return (
              <Pressable
                key={`${r}-${c}`}
                disabled={!selectable && !interactive && !onLongPressCard}
                onPress={() => (selectable ? onSelect?.() : interactive && onToggle?.(n))}
                onLongPress={onLongPressCard}
                delayLongPress={350}
                className="flex-1 aspect-square items-center justify-center"
              >
                <View className="flex-1 items-center justify-center">
                  <View className="w-6 h-6 rounded-full items-center justify-center border" style={cellStyle}>
                    <Text className="font-black text-[13px]" style={{ color: isFree || isLast ? LAST_INK : isDaubed ? '#ffffff' : colors.textSecondary }}>{display}</Text>
                  </View>
                </View>
              </Pressable>
            );
          })}
        </View>
      ))}
    </View>
  );
}