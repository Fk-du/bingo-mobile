import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

/**
 * How the player's board is ordered. A player holding several cards needs the
 * card closest to winning to be findable without scrolling past the rest.
 *
 * Every option is one-directional: the useful question is always "which card
 * is furthest along", never "which is least marked". Reversed variants were
 * removed, so 'fewestMarked' and 'fewestRows' are deliberately not valid.
 */
export const CARD_SORTS = ['cardOrder', 'mostCalled', 'mostRows', 'mostSquares', 'mostSmallCrosses', 'mostTs'] as const;
export type CardSort = (typeof CARD_SORTS)[number];

/** The colour a called/marked number is filled with. */
export type MarkColor = 'red' | 'green' | 'blue' | 'purple' | 'orange' | 'black';

/**
 * Fill and border for a daubed cell, per colour. Kept as raw hex rather than
 * Tailwind classes for the reason described in CardGrid: swapping a className
 * on state change drags NativeWind into a CSS-variable upgrade that crashes.
 */
export const MARK_COLORS: Record<MarkColor, { fill: string; border: string }> = {
  red: { fill: '#FF5C6C', border: '#FF5C6C99' },
  green: { fill: '#27AE60', border: '#27AE6099' },
  blue: { fill: '#2D9CDB', border: '#2D9CDB99' },
  purple: { fill: '#6B5BFF', border: '#6B5BFF99' },
  orange: { fill: '#F2994A', border: '#F2994A99' },
  // The near-black of the light theme's ink rather than a pure #000: a dead
  // black cell reads as a gap in the grid on an OLED screen. It is the one
  // colour that has to work in both themes, since the fill carries no alpha
  // for the theme to tint.
  black: { fill: '#1A1E2C', border: '#1A1E2C99' },
};

interface GameSettingsState {
  markColor: MarkColor;
  cardSort: CardSort;
  /**
   * Whether called numbers mark themselves on the player's cards. This is a
   * device preference, not a per-game one: the board follows it in every game,
   * and the screen keeps the server's per-card flag in step with it on entry.
   */
  autoMark: boolean;
  /** Whether a called number is announced out loud. */
  soundEnabled: boolean;
  setMarkColor: (c: MarkColor) => void;
  setCardSort: (s: CardSort) => void;
  setAutoMark: (v: boolean) => void;
  setSoundEnabled: (v: boolean) => void;
}

/**
 * Where the sound preference lived before it moved in here, written by the
 * number announcer as `1`/`0`. Folded into the store once on first read so a
 * player who had muted the calls does not get them back by the move.
 */
const LEGACY_SOUND_KEY = 'bingo-call-sound-muted';

/**
 * Values written by an earlier build, kept so a device that already has a sort
 * saved does not have its board silently fall back to card order.
 */
const LEGACY_CARD_SORTS: Record<string, CardSort> = {
  fewestMarked: 'cardOrder',
  fewestRows: 'cardOrder',
  // Worked in testing, but it ranked on pattern cells while the board is
  // ordered for marking, and the two disagreed often enough to confuse.
  closestToPattern: 'mostCalled',
};

export const useGameSettings = create<GameSettingsState>()(
  persist(
    (set) => ({
      markColor: 'red',
      cardSort: 'cardOrder',
      autoMark: false,
      soundEnabled: false,
      setMarkColor: (markColor) => set({ markColor }),
      setCardSort: (cardSort) => set({ cardSort }),
      setAutoMark: (autoMark) => set({ autoMark }),
      setSoundEnabled: (soundEnabled) => set({ soundEnabled }),
    }),
    {
      name: 'bingo.game-settings',
      storage: createJSONStorage(() => AsyncStorage),
      // A persisted value from an older build is coerced rather than trusted:
      // the board looks up the sort in a table, and an unknown key would throw
      // while rendering the game.
      merge: (persisted, current) => {
        const saved = (persisted ?? {}) as Partial<GameSettingsState>;
        const sort = saved.cardSort;
        if (saved.soundEnabled === undefined) {
          void AsyncStorage.getItem(LEGACY_SOUND_KEY).then((stored) => {
            if (stored !== null) {
              useGameSettings.setState({ soundEnabled: stored !== '1' });
              void AsyncStorage.removeItem(LEGACY_SOUND_KEY);
            }
          });
        }
        return {
          ...current,
          ...saved,
          cardSort:
            sort === undefined
              ? current.cardSort
              : ((CARD_SORTS as readonly string[]).includes(sort) ? sort : (LEGACY_CARD_SORTS[sort] ?? 'cardOrder')),
        };
      },
    }
  )
);

/**
 * Complete lines (rows, columns, diagonals) on a card. Used by the "most lines" sort,
 * which is a different signal from raw count: a card with three complete lines is
 * further along than one with ten scattered marks.
 */
export function countMarkedRows(numbers: number[][], marked: Set<number>): number {
  if (!numbers || numbers.length < 5) return 0;

  let lines = 0;
  const isMarked = (n: number | undefined, r: number, c: number): boolean => {
    // The free center at (2, 2) is always considered marked
    if (r === 2 && c === 2) return true;
    // A number is marked if it's in the marked set
    return n !== undefined && marked.has(n);
  };

  // Count complete rows
  for (let r = 0; r < 5; r++) {
    const row = numbers[r];
    if (row && row.length >= 5 && row.every((n, c) => isMarked(n, r, c))) {
      lines++;
    }
  }

  // Count complete columns
  for (let c = 0; c < 5; c++) {
    let colComplete = true;
    for (let r = 0; r < 5; r++) {
      if (!isMarked(numbers[r]?.[c], r, c)) {
        colComplete = false;
        break;
      }
    }
    if (colComplete) lines++;
  }

  // Count main diagonal (top-left to bottom-right)
  let diagComplete = true;
  for (let i = 0; i < 5; i++) {
    if (!isMarked(numbers[i]?.[i], i, i)) {
      diagComplete = false;
      break;
    }
  }
  if (diagComplete) lines++;

  // Count anti-diagonal (top-right to bottom-left)
  let antiDiagComplete = true;
  for (let i = 0; i < 5; i++) {
    if (!isMarked(numbers[i]?.[4 - i], i, 4 - i)) {
      antiDiagComplete = false;
      break;
    }
  }
  if (antiDiagComplete) lines++;

  return lines;
}

/**
 * Count complete 2×2 squares on a card. A square is formed when all 4 corners
 * of a 2×2 sub-grid are marked. Any 2×2 area that doesn't include the center
 * requires all 4 cells; squares that include the center (2,2) only require
 * the other 3 since the center is always free.
 */
export function countSmallSquares(numbers: number[][], marked: Set<number>): number {
  if (!numbers || numbers.length < 5) return 0;

  const isMarked = (r: number, c: number): boolean => {
    if (r === 2 && c === 2) return true; // Center is always marked
    return (numbers[r]?.[c] !== undefined && marked.has(numbers[r][c]));
  };

  let squares = 0;

  // Check all possible 2×2 squares (4 positions for each)
  for (let r = 0; r < 4; r++) {
    for (let c = 0; c < 4; c++) {
      // Check if all 4 corners of this 2×2 square are marked
      if (
        isMarked(r, c) &&
        isMarked(r, c + 1) &&
        isMarked(r + 1, c) &&
        isMarked(r + 1, c + 1)
      ) {
        squares++;
      }
    }
  }

  return squares;
}

/**
 * Count small crosses (plus signs) on a card. A small cross is 5 cells:
 * the centre plus its four orthogonal neighbours. Each cross is counted once
 * per centre cell that is fully marked. The free centre cell (2,2) is always
 * considered marked.
 */
export function countSmallCrosses(numbers: number[][], marked: Set<number>): number {
  if (!numbers || numbers.length < 5) return 0;

  const isMarked = (r: number, c: number): boolean => {
    if (r === 2 && c === 2) return true;
    return (numbers[r]?.[c] !== undefined && marked.has(numbers[r][c]));
  };

  let crosses = 0;
  for (let r = 1; r < 4; r++) {
    for (let c = 1; c < 4; c++) {
      if (isMarked(r - 1, c) && isMarked(r + 1, c) && isMarked(r, c - 1) && isMarked(r, c + 1) && isMarked(r, c)) {
        crosses++;
      }
    }
  }
  return crosses;
}

/**
 * Count T-shaped patterns on a card. A small T is 4 cells:
 * - 3 cells in a vertical or horizontal line (the bar)
 * - 1 cell perpendicular from the middle of the bar (the stem)
 *
 * Each stem direction is counted independently, so a bar with stems on both
 * sides counts as two Ts. Matches the backend's {@code BingoPatternRules.smallTShapes}.
 *
 * Examples of small Ts (center is the bar's middle, S is the stem):
 *   *.*.   ***   .*.   *.*
 *   ***    .*.   ***   .*.
 *   ...    *S.   ...   S.*
 * The free centre cell (2,2) is always considered marked.
 */
export function countTs(numbers: number[][], marked: Set<number>): number {
  if (!numbers || numbers.length < 5) return 0;

  const isMarked = (r: number, c: number): boolean => {
    if (r === 2 && c === 2) return true;
    return (numbers[r]?.[c] !== undefined && marked.has(numbers[r][c]));
  };

  let tCount = 0;

  // Horizontal T-shapes: 3 cells in a row + 1 stem above or below the middle
  for (let r = 0; r < 5; r++) {
    for (let c = 1; c < 4; c++) {
      if (isMarked(r, c - 1) && isMarked(r, c) && isMarked(r, c + 1)) {
        if (r > 0 && isMarked(r - 1, c)) tCount++;
        if (r < 4 && isMarked(r + 1, c)) tCount++;
      }
    }
  }

  // Vertical T-shapes: 3 cells in a column + 1 stem left or right of the middle
  for (let r = 1; r < 4; r++) {
    for (let c = 0; c < 5; c++) {
      if (isMarked(r - 1, c) && isMarked(r, c) && isMarked(r + 1, c)) {
        if (c > 0 && isMarked(r, c - 1)) tCount++;
        if (c < 4 && isMarked(r, c + 1)) tCount++;
      }
    }
  }

  return tCount;
}
