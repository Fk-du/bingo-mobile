const PATTERN_CELLS: Record<string, Set<string>> = {
  SINGLE_LINE: new Set(['2,0', '2,1', '2,2', '2,3', '2,4']),
  DOUBLE_LINE: new Set(['0,0', '0,1', '0,2', '0,3', '0,4', '4,0', '4,1', '4,2', '4,3', '4,4']),
  FULL_HOUSE: new Set(
    Array.from({ length: 5 }, (_, r) => Array.from({ length: 5 }, (_, c) => `${r},${c}`)).flat(),
  ),
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

export function patternCells(pattern?: string | null): Set<string> | null {
  if (!pattern) return null;
  return PATTERN_CELLS[pattern] ?? null;
}

export function customCellsFromJson(json?: string | null): Set<string> | null {
  if (!json) return null;
  try {
    const cells = JSON.parse(json) as number[][];
    if (!Array.isArray(cells)) return null;
    return new Set(cells.filter((c) => Array.isArray(c) && c.length === 2).map((c) => `${c[0]},${c[1]}`));
  } catch {
    return null;
  }
}

export function patternProgress(
  card: number[][],
  marks: Set<number>,
  pattern?: string | null,
): { done: number; total: number } | null {
  let cells = patternCells(pattern);
  if (pattern === 'CUSTOM') {
    cells = customCellsFromJson(null); // handled by caller with declared cells
  }
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