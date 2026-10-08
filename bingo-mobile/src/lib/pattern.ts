/**
 * Cell sets for the patterns that are not plain grids. `FULL_HOUSE` is the whole card:
 * it is the first entry in the canonical GamePatterns list, and it is a whole-card
 * pattern rather than a grid, so it is defined here instead of in PATTERN_GRIDS.
 */
const PATTERN_CELLS: Record<string, Set<string>> = {
  FULL_HOUSE: new Set(
    Array.from({ length: 5 }, (_, r) => Array.from({ length: 5 }, (_, c) => `${r},${c}`)).flat(),
  ),
};

/**
 * The patterns offered in the admin game-creation picker, written as five rows of five
 * characters (top row first): `*` is a cell the card must have called, `.` is irrelevant.
 * The centre cell is free on every card. Keep in sync with the backend's
 * WinningPatternGeometry so previews and progress match what the server settles.
 */
const PATTERN_GRIDS: Record<string, string> = {
  // Line ladders
  FOUR_LINES: '*****' + '*****' + '*...*' + '*...*' + '*...*',
  FIVE_LINES: '*****' + '*****' + '*****' + '**...' + '*....',
  SIX_LINES: '*****' + '*****' + '*****' + '**...' + '**...',
  SEVEN_LINES: '*****' + '*****' + '*.*..' + '*****' + '*.*.*',
  EIGHT_LINES: '*****' + '*****' + '*.*.*' + '*****' + '*.*.*',
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
  TWO_VERT_TWO_HORIZ_ONE_DIAG: '*****' + '**..*' + '*.*.*' + '*..**' + '*****',
  TWO_VERT_THREE_HORIZ: '*****' + '**...' + '**...' + '*****' + '*****',
  TWO_HORIZ_TWO_VERT_TWO_DIAG: '*****' + '**.**' + '*.*.*' + '**.**' + '*****',

  // Squares and dots
  FOUR_SQUARES: '**.**' + '**.**' + '.....' + '**.**' + '**.**',
  TWO_LINES_TWO_SQUARES: '*****' + '**...' + '.....' + '**...' + '*****',
  TWO_LINES_TWO_SEP_SQUARES: '**.**' + '**.**' + '*...*' + '*...*' + '*...*',
  THREE_SQUARES_FOUR_DOTS: '**.**' + '**.**' + '..*.*' + '**.*.' + '**..*',

  // T shapes and crosses
  LARGE_T_TWO_LINES: '*****' + '*****' + '..*..' + '..*..' + '*****',
  LARGE_T_THREE_LINES: '*****' + '*****' + '..*..' + '*****' + '*****',
  THREE_SMALL_T: '***..' + '.*...' + '*....' + '**.*.' + '*.***',
  LARGE_CROSS_TWO_SQUARES: '***..' + '***..' + '*****' + '..***' + '..***',
  THREE_SMALL_CROSSES: '.*...' + '***..' + '*****' + '..***' + '...*.',

  // Half card
  HALF_HOUSE: '*****' + '*****' + '*****' + '.....' + '.....',
};

/**
 * Alternative layouts for patterns that can be won through more than one shape, mirroring
 * the backend's WinningPatternGeometry.VARIANTS. The first entry is always the layout in
 * PATTERN_GRIDS, so previews and cell counts keep using the primary shape.
 *
 * HALF_HOUSE is half of the card in any of eight ways: three rows above or below a free
 * band, three columns to the left or right of one, or either side of either diagonal.
 * Every layout is exactly fifteen cells and includes the free centre.
 */
const PATTERN_VARIANTS: Record<string, string[]> = {
  HALF_HOUSE: [
    '*****' + '*****' + '*****' + '.....' + '.....', // top three rows
    '.....' + '.....' + '*****' + '*****' + '*****', // bottom three rows
    '***..' + '***..' + '***..' + '***..' + '***..', // left three columns
    '..***' + '..***' + '..***' + '..***' + '..***', // right three columns
    '*....' + '**...' + '***..' + '****.' + '*****', // main diagonal, lower-left half
    '*****' + '.****' + '..***' + '...**' + '....*', // main diagonal, upper-right half
    '....*' + '...**' + '..***' + '.****' + '*****', // anti-diagonal, lower-right half
    '*****' + '****.' + '***..' + '**...' + '*....', // anti-diagonal, upper-left half
  ],
};

const PATTERN_VARIANT_CELLS: Record<string, Set<string>[]> = {};
for (const [code, grids] of Object.entries(PATTERN_VARIANTS)) {
  PATTERN_VARIANT_CELLS[code] = grids.map(cellsFromGrid);
}

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
  const variants = pattern ? PATTERN_VARIANT_CELLS[pattern] : undefined;
  if (!variants || variants.length === 0) {
    return computeProgress(card, marks, cells);
  }
  // Multi-shape patterns report the layout closest to winning so far.
  let best = computeProgress(card, marks, variants[0]);
  for (let i = 1; i < variants.length; i++) {
    const progress = computeProgress(card, marks, variants[i]);
    if (progress.done / progress.total > best.done / best.total) {
      best = progress;
    }
  }
  return best;
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