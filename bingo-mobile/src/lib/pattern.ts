/**
 * Retired patterns, kept only so finished games created before the picker was rebuilt around
 * the canonical GamePatterns list still render their board progress. `FULL_HOUSE` is not
 * retired: it is the first entry in that list, and it is a whole-card pattern rather than a
 * grid, so it is defined here instead of in PATTERN_GRIDS.
 */
const PATTERN_CELLS: Record<string, Set<string>> = {
  FULL_HOUSE: new Set(
    Array.from({ length: 5 }, (_, r) => Array.from({ length: 5 }, (_, c) => `${r},${c}`)).flat(),
  ),
  SINGLE_LINE: new Set(['2,0', '2,1', '2,2', '2,3', '2,4']),
  DOUBLE_LINE: new Set(['0,0', '0,1', '0,2', '0,3', '0,4', '4,0', '4,1', '4,2', '4,3', '4,4']),
  X_SHAPE: new Set(['0,0', '1,1', '2,2', '3,3', '4,4', '0,4', '1,3', '3,1', '4,0']),
  L_SHAPE: new Set(['0,0', '1,0', '2,0', '3,0', '4,0', '4,1', '4,2', '4,3', '4,4']),
  T_SHAPE: new Set(['0,0', '0,1', '0,2', '0,3', '0,4', '1,2', '2,2', '3,2', '4,2']),
  POSTAGE_STAMP: new Set([
    '0,0', '0,1', '1,0', '1,1',
    '0,3', '0,4', '1,3', '1,4',
    '3,0', '3,1', '4,0', '4,1',
    '3,3', '3,4', '4,3', '4,4',
  ]),
  PLUS: new Set(['2,0', '2,1', '2,2', '2,3', '2,4', '0,2', '1,2', '3,2', '4,2']),
  FRAME: new Set([
    '0,0', '0,1', '0,2', '0,3', '0,4',
    '4,0', '4,1', '4,2', '4,3', '4,4',
    '1,0', '2,0', '3,0',
    '1,4', '2,4', '3,4',
  ]),
  DIAMOND: new Set(['1,1', '1,3', '2,2', '3,1', '3,3']),
  Z_SHAPE: new Set([
    '0,0', '0,1', '0,2', '0,3', '0,4',
    '1,1', '2,2', '3,3',
    '4,0', '4,1', '4,2', '4,3', '4,4',
  ]),
};

PATTERN_CELLS.BLACKOUT = PATTERN_CELLS.FULL_HOUSE;

/**
 * The patterns offered in the admin game-creation picker, written as five rows of five
 * characters (top row first): `*` is a cell the card must have called, `.` is irrelevant.
 * The centre cell is free on every card. Keep in sync with the backend's
 * WinningPatternGeometry so previews and progress match what the server settles.
 */
const PATTERN_GRIDS: Record<string, string> = {
  // Line ladders
  FOUR_LINES: '*****' + '*****' + '*...*' + '*...*' + '*...*',
  FIVE_LINES: '*****' + '*****' + '*...*' + '*..**' + '*...*',
  SIX_LINES: '*****' + '*****' + '*...*' + '**.**' + '*...*',
  SEVEN_LINES: '*****' + '*****' + '*...*' + '**.**' + '*****',
  EIGHT_LINES: '*****' + '*****' + '**..*' + '**.**' + '*****',
  THREE_LINES_ONE_DIAG: '*****' + '*****' + '..*..' + '...*.' + '*****',
  FOUR_LINES_TOUCH_FREE: '*.*.*' + '.***.' + '*****' + '.***.' + '*.*.*',

  // Line ladders that never rely on the free centre
  THREE_LINES_NO_FREE_DISJOINT: '*****' + '*****' + '.....' + '.....' + '*****',
  FOUR_LINES_NO_FREE_DISJOINT: '*****' + '*...*' + '*...*' + '*...*' + '*****',
  FOUR_LINES_NO_FREE: '*****' + '*****' + '*....' + '*....' + '*****',
  FIVE_LINES_NO_FREE: '*****' + '*****' + '*...*' + '*...*' + '*****',
  TWO_TOUCH_TWO_NO_TOUCH: '*****' + '..*..' + '*****' + '..*..' + '*****',

  // Vertical / horizontal / diagonal mixes
  TWO_VERT_TWO_HORIZ: '*****' + '**...' + '**...' + '**...' + '*****',
  TWO_VERT_TWO_HORIZ_ONE_DIAG: '*****' + '**...' + '***..' + '**..*' + '*****',
  TWO_VERT_THREE_HORIZ: '*****' + '**...' + '**...' + '*****' + '*****',
  TWO_HORIZ_TWO_VERT_TWO_DIAG: '*****' + '**..*' + '***..' + '**..*' + '*****',

  // Squares, rectangles and dots
  FOUR_SQUARES: '**.**' + '**.**' + '.....' + '**.**' + '**.**',
  TWO_LINES_TWO_SQUARES: '*****' + '.....' + '**.**' + '**.**' + '*****',
  TWO_LINES_TWO_SEP_SQUARES: '*...*' + '**.*.' + '*.**.' + '*.**.' + '*..**',
  TWO_LINES_TWO_RECTANGLES: '*****' + '.....' + '*...*' + '*...*' + '*****',
  THREE_SQUARES_FOUR_DOTS: '*****' + '**.**' + '*...*' + '**...' + '**.*.',
  THREE_RECTANGLES: '****.' + '.....' + '****.' + '.....' + '****.',

  // T shapes and crosses
  LARGE_T_TWO_LINES: '*****' + '*****' + '..*..' + '..*..' + '*****',
  LARGE_T_THREE_LINES: '*****' + '*****' + '..*..' + '*****' + '*****',
  THREE_SMALL_T: '****.' + '.***.' + '...**' + '...**' + '....*',
  LARGE_CROSS_TWO_SQUARES: '***..' + '***..' + '*****' + '..***' + '..***',
  THREE_SMALL_CROSSES: '.***.' + '*.*..' + '**...' + '*.*..' + '.***.',

  // Half card
  HALF_HOUSE: '*****' + '*****' + '.....' + '.....' + '.....',
};

function cellsFromGrid(grid: string): Set<string> {
  const cells = new Set<string>();
  for (let row = 0; row < 5; row++) {
    for (let col = 0; col < 5; col++) {
      if (grid[row * 5 + col] === '*') cells.add(`${row},${col}`);
    }
  }
  return cells;
}

for (const [code, grid] of Object.entries(PATTERN_GRIDS)) {
  PATTERN_CELLS[code] = cellsFromGrid(grid);
}

export function patternCells(pattern?: string | null): Set<string> | null {
  if (!pattern) return null;
  return PATTERN_CELLS[pattern] ?? null;
}

export function patternProgress(
  card: number[][],
  marks: Set<number>,
  pattern?: string | null,
): { done: number; total: number } | null {
  const cells = patternCells(pattern);
  if (cells === null) {
    return null;
  }
  return computeProgress(card, marks, cells);
}

export function computeProgress(
  card: number[][],
  marks: Set<number>,
  cells: Set<string>,
): { done: number; total: number } {
  let done = 0;
  for (const key of cells) {
    const [r, c] = key.split(',').map(Number);
    if (r === 2 && c === 2) {
      done++;
      continue;
    }
    if (marks.has(card[r]?.[c])) done++;
  }
  return { done, total: cells.size };
}