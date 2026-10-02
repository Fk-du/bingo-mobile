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
export const CARD_SORTS = ['cardOrder', 'mostMarked', 'mostRows'] as const;
export type CardSort = (typeof CARD_SORTS)[number];

/** The colour a called/marked number is filled with. */
export type MarkColor = 'red' | 'green' | 'blue' | 'purple' | 'orange';

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
};

interface GameSettingsState {
  markColor: MarkColor;
  cardSort: CardSort;
  setMarkColor: (c: MarkColor) => void;
  setCardSort: (s: CardSort) => void;
}

/**
 * Values written by an earlier build, kept so a device that already has a sort
 * saved does not have its board silently fall back to card order.
 */
const LEGACY_CARD_SORTS: Record<string, CardSort> = {
  fewestMarked: 'cardOrder',
  fewestRows: 'cardOrder',
  // Worked in testing, but it ranked on pattern cells while the board is
  // ordered for marking, and the two disagreed often enough to confuse.
  closestToPattern: 'mostMarked',
};

export const useGameSettings = create<GameSettingsState>()(
  persist(
    (set) => ({
      markColor: 'red',
      cardSort: 'cardOrder',
      setMarkColor: (markColor) => set({ markColor }),
      setCardSort: (cardSort) => set({ cardSort }),
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
 * Whole rows a card has marked. Used by the "most lines" sorts, which is a
 * different signal from raw count: a card with three complete rows is further
 * along than one with ten scattered marks.
 */
export function countMarkedRows(numbers: number[][], marked: Set<number>): number {
  let rows = 0;
  for (let r = 0; r < numbers.length && r < 5; r++) {
    const row = numbers[r] ?? [];
    if (row.length === 0) continue;
    const complete = row.every((n, c) => (r === 2 && c === 2 ? true : marked.has(n)));
    if (complete) rows++;
  }
  return rows;
}
