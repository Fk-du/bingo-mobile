package com.bingo.app.tenant.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Semantic evaluator for the canonical winning patterns offered in the admin picker.
 *
 * <p>The grids in {@link WinningPatternGeometry} are the demonstration layouts of each
 * pattern; a player is not required to reproduce them cell for cell. Each pattern is
 * validated as a <em>family</em> of every arrangement its name describes: a code named
 * for N lines wins on any N complete lines whether or not they are the lines in the
 * demo grid, a code named for N 2x2 blocks wins on any N complete blocks, and so on.
 *
 * <p>The centre cell (2,2) is free on every card, so it is always considered called.
 *
 * <p>HALF_HOUSE is the exception: its eight half-card layouts <em>are</em> the whole
 * family, so it stays a cell-exact match through {@link WinningPatternGeometry#variants}.
 */
public final class BingoPatternRules {

    private BingoPatternRules() {
    }

    // Line bookkeeping: rows occupy bits 0-4, columns 5-9, the two diagonals 10-11.
    private static final int ROW_MASK = 0x1F;
    private static final int COL_MASK = 0x1F << 5;
    private static final int DIAG_MASK = 0x3 << 10;
    /** The four lines that pass through the free centre. */
    private static final int CENTRE_LINES = (1 << 2) | (1 << 7) | (1 << 10) | (1 << 11);
    private static final int CENTRE_CELL = 1 << (2 * 5 + 2);

    /**
     * Whether the card wins the given canonical picker pattern under the current calls.
     *
     * @param card   5x5 card, centre cell always free
     * @param called the numbers called so far (the centre is added implicitly)
     * @param code   one of {@link WinningPatternGeometry#codes()}
     */
    public static boolean wins(int[][] card, Set<Integer> called, String code) {
        if (card == null || code == null) {
            return false;
        }
        boolean[][] m = marked(card, called);
        if ("HALF_HOUSE".equals(code)) {
            return halfHouse(m, code);
        }

        int lines = completeLinesMask(m);
        int rows = Integer.bitCount(lines & ROW_MASK);
        int cols = Integer.bitCount(lines & COL_MASK);
        int diags = Integer.bitCount(lines & DIAG_MASK);
        int total = rows + cols + diags;
        int centre = Integer.bitCount(lines & CENTRE_LINES);
        int noFree = total - centre;

        List<Integer> blocks = completeBlocks(m);
        int squares = blocks.size();
        int dots = dotCount(m, blocks);

        return switch (code) {
            // --- Line ladders --------------------------------------------------------
            case "FOUR_LINES" -> total >= 4;
            case "FIVE_LINES" -> total >= 5;
            case "SIX_LINES" -> total >= 6;
            case "SEVEN_LINES" -> total >= 7;
            case "EIGHT_LINES" -> total >= 8;
            case "THREE_LINES_ONE_DIAG" -> rows + cols >= 3 && diags >= 1;
            case "FOUR_LINES_TOUCH_FREE" -> centre >= 4;

            // --- Line ladders that never rely on the free centre --------------------
            case "THREE_LINES_NO_FREE_DISJOINT" -> noFree >= 3;
            case "FOUR_LINES_NO_FREE_DISJOINT", "FOUR_LINES_NO_FREE" -> noFree >= 4;
            case "FIVE_LINES_NO_FREE" -> noFree >= 5;
            case "TWO_TOUCH_TWO_NO_TOUCH" -> centre >= 2 && noFree >= 2;

            // --- Vertical / horizontal / diagonal mixes -----------------------------
            case "TWO_VERT_TWO_HORIZ" -> cols >= 2 && rows >= 2;
            case "TWO_VERT_TWO_HORIZ_ONE_DIAG" -> cols >= 2 && rows >= 2 && diags >= 1;
            case "TWO_VERT_THREE_HORIZ" -> cols >= 2 && rows >= 3;
            case "TWO_HORIZ_TWO_VERT_TWO_DIAG" -> rows >= 2 && cols >= 2 && diags >= 2;

            // --- Squares, rectangles and dots ----------------------------------------
            case "FOUR_SQUARES" -> packs(blocks, 4);
            case "TWO_LINES_TWO_SQUARES" -> total >= 2 && squares >= 2;
            case "TWO_LINES_TWO_SEP_SQUARES" -> total >= 2 && packs(blocks, 2);
            case "THREE_SQUARES_FOUR_DOTS" -> squares >= 3 && dots >= 4;

            // --- T shapes and crosses ------------------------------------------------
            case "LARGE_T_TWO_LINES" -> largeT(m, lines, 2);
            case "LARGE_T_THREE_LINES" -> largeT(m, lines, 3);
            case "THREE_SMALL_T" -> total >= 1 && packs(smallTShapes(m), 2);
            case "LARGE_CROSS_TWO_SQUARES" -> rowComplete(m, 2) && colComplete(m, 2) && squares >= 2;
            case "THREE_SMALL_CROSSES" -> twoCrossesPlusSeparateLine(m);

            default -> false;
        };
    }

    /**
     * Whether the last number called "helped make the pattern": its marked cell is part of
     * at least one complete component the pattern counts — a line, a 2x2 block, a small T,
     * a cross, a free dot, or the winning half/house layout.
     *
     * <p>A component only becomes complete when its last missing cell is called, so a number
     * that lies on no complete component added nothing to the card. A pattern already
     * satisfied by an earlier call therefore only wins again once a later call completes a
     * further component — which is exactly how a player who was late to shout Bingo may
     * still claim with extra lines or shapes beyond the demonstrated pattern. A number that
     * is absent from the card, or is an isolated mark, never helps.
     */
    public static boolean lastNumberHelps(int[][] card, Set<Integer> called, int lastNumber, String code) {
        if (card == null || code == null || card.length != 5) {
            return false;
        }
        int cell = cellOf(card, lastNumber);
        if (cell < 0) {
            return false;
        }
        int row = cell / 5;
        int col = cell % 5;
        boolean[][] m = marked(card, called);
        if (!m[row][col]) {
            return false;
        }

        int lines = completeLinesMask(m);
        List<Integer> blocks = completeBlocks(m);
        int cellBit = cellMask(row, col);

        return switch (code) {
            // The winning house always contains the last cell.
            case "FULL_HOUSE" -> true;
            case "HALF_HOUSE" -> halfHouseContains(m, row, col);

            // Any complete line counts (rows, columns and diagonals).
            case "FOUR_LINES", "FIVE_LINES", "SIX_LINES", "SEVEN_LINES", "EIGHT_LINES",
                 "THREE_LINES_ONE_DIAG", "TWO_VERT_TWO_HORIZ_ONE_DIAG",
                 "TWO_HORIZ_TWO_VERT_TWO_DIAG", "TWO_TOUCH_TWO_NO_TOUCH",
                 "LARGE_T_TWO_LINES", "LARGE_T_THREE_LINES" -> lineCovers(lines, row, col);

            // Only lines that never rely on the free centre.
            case "THREE_LINES_NO_FREE_DISJOINT", "FOUR_LINES_NO_FREE_DISJOINT",
                 "FOUR_LINES_NO_FREE", "FIVE_LINES_NO_FREE" ->
                    lineCovers(nonCentreLines(lines), row, col);

            // Only lines through the free centre.
            case "FOUR_LINES_TOUCH_FREE" -> lineCovers(lines & CENTRE_LINES, row, col);

            // Rows and columns, plus 2x2 blocks.
            case "TWO_VERT_TWO_HORIZ", "TWO_VERT_THREE_HORIZ", "LARGE_CROSS_TWO_SQUARES" ->
                    lineCovers(lines & (ROW_MASK | COL_MASK), row, col) || inAny(blocks, cellBit);

            // Any line, plus blocks.
            case "TWO_LINES_TWO_SQUARES", "TWO_LINES_TWO_SEP_SQUARES" ->
                    lineCovers(lines, row, col) || inAny(blocks, cellBit);

            case "FOUR_SQUARES" -> inAny(blocks, cellBit);

            // A block, or an isolated marked cell counting as a free dot.
            case "THREE_SQUARES_FOUR_DOTS" ->
                    inAny(blocks, cellBit) || isDot(m, blocks, row, col);

            case "THREE_SMALL_T" -> lineCovers(lines, row, col) || inAny(smallTShapes(m), cellBit);
            case "THREE_SMALL_CROSSES" -> lineCovers(lines, row, col) || inAny(fullCrossMasks(m), cellBit);

            default -> false;
        };
    }

    /** Grid index of {@code number} on the card, or -1 when the card does not hold it. */
    private static int cellOf(int[][] card, int number) {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (card[r][c] == number) {
                    return r * 5 + c;
                }
            }
        }
        return -1;
    }

    /** Complete lines that never use the free centre (non-centre rows and columns). */
    private static int nonCentreLines(int lines) {
        return lines & (ROW_MASK | COL_MASK) & ~CENTRE_LINES;
    }

    /** Whether cell (row,col) lies on any complete line in the mask. */
    private static boolean lineCovers(int lines, int row, int col) {
        return (lines & (1 << row)) != 0
                || (lines & (1 << (5 + col))) != 0
                || (row == col && (lines & (1 << 10)) != 0)
                || (row + col == 4 && (lines & (1 << 11)) != 0);
    }

    private static boolean inAny(List<Integer> masks, int cellBit) {
        for (int mask : masks) {
            if ((mask & cellBit) != 0) {
                return true;
            }
        }
        return false;
    }

    /** A marked cell outside every complete block (the free dot of a squares-and-dots card). */
    private static boolean isDot(boolean[][] m, List<Integer> blocks, int row, int col) {
        if (!m[row][col]) {
            return false;
        }
        int cellBit = cellMask(row, col);
        for (int block : blocks) {
            if ((block & cellBit) != 0) {
                return false;
            }
        }
        return true;
    }

    /** Whether a completed half-house layout that contains cell (row,col) is on the card. */
    private static boolean halfHouseContains(boolean[][] m, int row, int col) {
        for (List<int[]> layout : WinningPatternGeometry.variants("HALF_HOUSE")) {
            boolean complete = true;
            boolean contains = false;
            for (int[] cell : layout) {
                if (!m[cell[0]][cell[1]]) {
                    complete = false;
                    break;
                }
                if (cell[0] == row && cell[1] == col) {
                    contains = true;
                }
            }
            if (complete && contains) {
                return true;
            }
        }
        return false;
    }

    /** The centre is free, so a card cell is "marked" whenever it is called or is (2,2). */
    private static boolean[][] marked(int[][] card, Set<Integer> called) {
        boolean[][] m = new boolean[5][5];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                m[r][c] = called.contains(card[r][c]) || (r == 2 && c == 2);
            }
        }
        return m;
    }

    /** HALF_HOUSE stays cell-exact: it wins as soon as any one of its eight layouts is complete. */
    private static boolean halfHouse(boolean[][] m, String code) {
        for (List<int[]> layout : WinningPatternGeometry.variants(code)) {
            boolean complete = true;
            for (int[] cell : layout) {
                if (!m[cell[0]][cell[1]]) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                return true;
            }
        }
        return false;
    }

    /** Bitmask of the twelve lines fully covered by the marked cells. */
    private static int completeLinesMask(boolean[][] m) {
        int mask = 0;
        for (int r = 0; r < 5; r++) {
            if (rowComplete(m, r)) {
                mask |= 1 << r;
            }
        }
        for (int c = 0; c < 5; c++) {
            if (colComplete(m, c)) {
                mask |= 1 << (5 + c);
            }
        }
        if (diagComplete(m)) {
            mask |= 1 << 10;
        }
        if (antiDiagComplete(m)) {
            mask |= 1 << 11;
        }
        return mask;
    }

    private static boolean rowComplete(boolean[][] m, int r) {
        return m[r][0] && m[r][1] && m[r][2] && m[r][3] && m[r][4];
    }

    private static boolean colComplete(boolean[][] m, int c) {
        return m[0][c] && m[1][c] && m[2][c] && m[3][c] && m[4][c];
    }

    private static boolean diagComplete(boolean[][] m) {
        return m[0][0] && m[1][1] && m[2][2] && m[3][3] && m[4][4];
    }

    private static boolean antiDiagComplete(boolean[][] m) {
        return m[0][4] && m[1][3] && m[2][2] && m[3][1] && m[4][0];
    }

    /**
     * A large T in any of its four orientations: a full row at the top or bottom with the
     * full middle column, or a full column at the far left or right with the full middle
     * row. The "two/three lines" are the extra complete lines beyond the two that make up
     * the T itself, on whatever orientation is present. The demo grids draw the top row
     * plus the middle column, which the two/three extra lines reproduce (THREE_LINES
     * completes rows 1, 3 and 4, TWO_LINES rows 1 and 4), but a card in any other
     * orientation, or with the extra lines anywhere else, wins just the same.
     */
    private static boolean largeT(boolean[][] m, int completeLines, int extraLines) {
        boolean barAcrossTop = rowComplete(m, 0) && colComplete(m, 2);
        boolean barAcrossBottom = rowComplete(m, 4) && colComplete(m, 2);
        boolean barDownLeft = colComplete(m, 0) && rowComplete(m, 2);
        boolean barDownRight = colComplete(m, 4) && rowComplete(m, 2);
        if (!(barAcrossTop || barAcrossBottom || barDownLeft || barDownRight)) {
            return false;
        }
        return Integer.bitCount(completeLines) - 2 >= extraLines;
    }

    /** Every complete 2x2 block of marked cells, as a cell bitmask. */
    private static List<Integer> completeBlocks(boolean[][] m) {
        List<Integer> blocks = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if (m[r][c] && m[r][c + 1] && m[r + 1][c] && m[r + 1][c + 1]) {
                    blocks.add(cellMask(r, c) | cellMask(r, c + 1) | cellMask(r + 1, c) | cellMask(r + 1, c + 1));
                }
            }
        }
        return blocks;
    }

    /**
     * Number of marked cells outside every complete 2x2 block, plus the free centre which
     * counts as a dot by definition even when a block happens to cover it.
     */
    private static int dotCount(boolean[][] m, List<Integer> blocks) {
        int all = markedMask(m);
        int blockUnion = 0;
        for (int block : blocks) {
            blockUnion |= block;
        }
        int dots = Integer.bitCount(all & ~blockUnion);
        if ((blockUnion & CENTRE_CELL) != 0) {
            dots++; // the centre is always a dot
        }
        return dots;
    }

    /** The cell masks of every complete five-cell plus sign. */
    private static List<Integer> fullCrossMasks(boolean[][] m) {
        List<Integer> crosses = new ArrayList<>();
        for (int r = 1; r < 4; r++) {
            for (int c = 1; c < 4; c++) {
                if (m[r][c] && m[r - 1][c] && m[r + 1][c] && m[r][c - 1] && m[r][c + 1]) {
                    crosses.add(cellMask(r, c) | cellMask(r - 1, c) | cellMask(r + 1, c)
                            | cellMask(r, c - 1) | cellMask(r, c + 1));
                }
            }
        }
        return crosses;
    }

    /**
     * "Two disconnected crosses + a line": two full plus signs that share no cell with
     * each other <em>and</em> a complete line that shares no cell with either cross. A
     * cross that a line runs straight through would make the three a single shape, so the
     * line must stand apart for the name to hold.
     */
    private static boolean twoCrossesPlusSeparateLine(boolean[][] m) {
        List<Integer> crosses = fullCrossMasks(m);
        for (int i = 0; i < crosses.size(); i++) {
            int first = crosses.get(i);
            for (int j = i + 1; j < crosses.size(); j++) {
                int second = crosses.get(j);
                if ((first & second) != 0) {
                    continue; // the two crosses must be disconnected
                }
                int both = first | second;
                for (int line : completeLineCellMasks(m)) {
                    if ((line & both) == 0) {
                        return true; // a line that steers clear of both crosses
                    }
                }
            }
        }
        return false;
    }

    /** The cell set of every line fully covered by the marked cells. */
    private static List<Integer> completeLineCellMasks(boolean[][] m) {
        List<Integer> lines = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            if (rowComplete(m, r)) {
                lines.add(cellMask(r, 0) | cellMask(r, 1) | cellMask(r, 2) | cellMask(r, 3) | cellMask(r, 4));
            }
        }
        for (int c = 0; c < 5; c++) {
            if (colComplete(m, c)) {
                lines.add(cellMask(0, c) | cellMask(1, c) | cellMask(2, c) | cellMask(3, c) | cellMask(4, c));
            }
        }
        if (diagComplete(m)) {
            lines.add(cellMask(0, 0) | cellMask(1, 1) | cellMask(2, 2) | cellMask(3, 3) | cellMask(4, 4));
        }
        if (antiDiagComplete(m)) {
            lines.add(cellMask(0, 4) | cellMask(1, 3) | cellMask(2, 2) | cellMask(3, 1) | cellMask(4, 0));
        }
        return lines;
    }

    /** Every 4-cell small T (three in a row plus a perpendicular stem), as a cell bitmask. */
    private static List<Integer> smallTShapes(boolean[][] m) {
        List<Integer> shapes = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            for (int c = 1; c < 4; c++) {
                addT(shapes, allMarked(m, r, c - 1, r, c, r, c + 1, r + 1, c)); // stem below
                addT(shapes, allMarked(m, r, c - 1, r, c, r, c + 1, r - 1, c)); // stem above
            }
        }
        for (int r = 1; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                addT(shapes, allMarked(m, r - 1, c, r, c, r + 1, c, r, c + 1)); // stem right
                addT(shapes, allMarked(m, r - 1, c, r, c, r + 1, c, r, c - 1)); // stem left
            }
        }
        return shapes;
    }

    private static void addT(List<Integer> shapes, int mask) {
        if (mask != 0) {
            shapes.add(mask);
        }
    }

    /** Every step cell of a single shape, or 0 when any part is off-grid or unmarked. */
    private static int allMarked(boolean[][] m, int... cells) {
        int mask = 0;
        for (int i = 0; i < cells.length; i += 2) {
            int r = cells[i], c = cells[i + 1];
            if (r < 0 || r > 4 || c < 0 || c > 4 || !m[r][c]) {
                return 0;
            }
            mask |= cellMask(r, c);
        }
        return mask;
    }

    /** Whether any {@code need} shapes/blocks that share no cell can be picked at once. */
    private static boolean packs(List<Integer> shapes, int need) {
        return packMore(shapes, need, 0, 0, 0);
    }

    private static boolean packMore(List<Integer> shapes, int need, int index, int used, int picked) {
        if (picked >= need) {
            return true;
        }
        if (picked + (shapes.size() - index) < need) {
            return false;
        }
        for (int i = index; i < shapes.size(); i++) {
            int mask = shapes.get(i);
            if ((used & mask) == 0 && packMore(shapes, need, i + 1, used | mask, picked + 1)) {
                return true;
            }
        }
        return false;
    }

    private static int markedMask(boolean[][] m) {
        int mask = 0;
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (m[r][c]) {
                    mask |= cellMask(r, c);
                }
            }
        }
        return mask;
    }

    private static int cellMask(int r, int c) {
        return 1 << (r * 5 + c);
    }
}