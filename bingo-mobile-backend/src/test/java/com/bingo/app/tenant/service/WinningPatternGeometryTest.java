package com.bingo.app.tenant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the grid patterns offered in the admin game-creation picker: every code must have
 * a layout, must win once its cells are called, must not win with one cell missing, and must
 * never be satisfiable by the free centre alone.
 */
class WinningPatternGeometryTest {

    private final GameEngineService engine = new GameEngineService(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
    );

    /** Every picker code, so a newly added pattern cannot be forgotten here. */
    private static final List<String> EXPECTED_CODES = List.of(
            "FOUR_LINES", "FIVE_LINES", "SIX_LINES", "SEVEN_LINES", "EIGHT_LINES",
            "THREE_LINES_ONE_DIAG", "FOUR_LINES_TOUCH_FREE",
            "THREE_LINES_NO_FREE_DISJOINT", "FOUR_LINES_NO_FREE_DISJOINT", "FOUR_LINES_NO_FREE",
            "FIVE_LINES_NO_FREE", "TWO_TOUCH_TWO_NO_TOUCH",
            "TWO_VERT_TWO_HORIZ", "TWO_VERT_TWO_HORIZ_ONE_DIAG", "TWO_VERT_THREE_HORIZ",
            "TWO_HORIZ_TWO_VERT_TWO_DIAG",
            "FOUR_SQUARES", "TWO_LINES_TWO_SQUARES", "TWO_LINES_TWO_SEP_SQUARES",
            "THREE_SQUARES_FOUR_DOTS",
            "LARGE_T_TWO_LINES", "LARGE_T_THREE_LINES", "THREE_SMALL_T",
            "LARGE_CROSS_TWO_SQUARES", "THREE_SMALL_CROSSES",
            "HALF_HOUSE"
    );

    private int[][] fullCard() {
        int n = 1;
        int[][] card = new int[5][5];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                card[r][c] = (r == 2 && c == 2) ? 0 : n++;
            }
        }
        return card;
    }

    private static List<Integer> calledCells(int[][] card, List<int[]> coords) {
        List<Integer> called = new ArrayList<>();
        for (int[] rc : coords) {
            called.add(card[rc[0]][rc[1]]);
        }
        return called;
    }

    @Test
    @DisplayName("every picker pattern has a layout, and only those layouts")
    void layoutsCoverPicker() {
        assertAll(
                () -> assertEquals(EXPECTED_CODES.size(), WinningPatternGeometry.codes().size(),
                        "geometry count"),
                () -> assertEquals(Set.copyOf(EXPECTED_CODES), WinningPatternGeometry.codes())
        );
    }

    @Test
    @DisplayName("each pattern wins when all its cells are called")
    void winsWhenComplete() {
        int[][] card = fullCard();
        for (String code : EXPECTED_CODES) {
            List<int[]> cells = WinningPatternGeometry.cells(code);
            assertFalse(cells.isEmpty(), code + " must define cells");
            assertTrue(engine.validateBingo(card, calledCells(card, cells), code),
                    code + " should win with every cell called");
        }
    }

    @Test
    @DisplayName("each pattern loses on some one-cell-short card")
    void failsOnACloseButIncompleteCard() {
        int[][] card = fullCard();
        for (String code : EXPECTED_CODES) {
            List<int[]> cells = SemanticDemoCells.winningCells(code);
            boolean found = false;
            for (int[] drop : cells) {
                if (drop[0] == 2 && drop[1] == 2) {
                    continue; // free centre is never required
                }
                if (!engine.validateBingo(card, calledCells(card, withoutCell(cells, drop)), code)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found,
                    code + " must lose on at least one one-cell-short card (semantic near-miss)");
        }
    }

    private static List<int[]> withoutCell(List<int[]> cells, int[] dropped) {
        List<int[]> out = new ArrayList<>();
        for (int[] cell : cells) {
            if (cell[0] != dropped[0] || cell[1] != dropped[1]) {
                out.add(cell);
            }
        }
        return out;
    }

    @Test
    @DisplayName("no pattern is won by the free centre alone")
    void freeCentreNeverWinsAlone() {
        int[][] card = fullCard();
        for (String code : EXPECTED_CODES) {
            assertFalse(engine.validateBingo(card, List.of(0), code),
                    code + " must require at least one real number");
        }
    }

    @Test
    @DisplayName("the *_NO_FREE patterns never include the free centre")
    void noFreePatternsSkipCentre() {
        for (String code : List.of("THREE_LINES_NO_FREE_DISJOINT", "FOUR_LINES_NO_FREE_DISJOINT",
                "FOUR_LINES_NO_FREE", "FIVE_LINES_NO_FREE")) {
            assertFalse(WinningPatternGeometry.usesFreeCentre(code), code + " must avoid (2,2)");
        }
    }

    /**
     * A pattern whose code promises N lines must actually complete N lines. The grids used to be
     * checked only for "wins when complete" / "fails when one cell is missing", which any layout
     * passes, so several names drifted away from their shapes: FIVE_LINES completed four lines,
     * SIX_LINES four, SEVEN_LINES five, and both of the diagonal mixes had no diagonal at all.
     * This asserts the count so the two cannot drift apart again.
     */
    @Test
    @DisplayName("a pattern named for N lines completes exactly N lines")
    void lineCountsMatchTheirNames() {
        // The free centre always counts as called, so a line through it is complete for free.
        record Expectation(String code, int lines) {
        }
        List<Expectation> expectations = List.of(
                new Expectation("FOUR_LINES", 4),
                new Expectation("FIVE_LINES", 5),
                new Expectation("SIX_LINES", 6),
                new Expectation("SEVEN_LINES", 7),
                new Expectation("EIGHT_LINES", 8),
                new Expectation("THREE_LINES_NO_FREE_DISJOINT", 3),
                new Expectation("FOUR_LINES_NO_FREE_DISJOINT", 4),
                new Expectation("FOUR_LINES_NO_FREE", 4),
                new Expectation("FIVE_LINES_NO_FREE", 5),
                new Expectation("THREE_LINES_ONE_DIAG", 4),
                new Expectation("TWO_TOUCH_TWO_NO_TOUCH", 4),
                new Expectation("TWO_VERT_TWO_HORIZ", 4),
                new Expectation("TWO_VERT_TWO_HORIZ_ONE_DIAG", 5),
                new Expectation("TWO_VERT_THREE_HORIZ", 6),
                new Expectation("TWO_HORIZ_TWO_VERT_TWO_DIAG", 6),
                new Expectation("TWO_LINES_TWO_SQUARES", 2),
                new Expectation("TWO_LINES_TWO_SEP_SQUARES", 2),
                new Expectation("LARGE_T_TWO_LINES", 4),
                new Expectation("LARGE_T_THREE_LINES", 7),
                new Expectation("FOUR_LINES_TOUCH_FREE", 4)
        );

        for (Expectation e : expectations) {
            Set<String> complete = completedLines(WinningPatternGeometry.cells(e.code()));
            assertEquals(e.lines(), complete.size(),
                    e.code() + " is named for " + e.lines() + " lines but completes " + complete);
        }
    }

    /**
     * Which of the card's twelve possible lines (five rows, five columns, two diagonals) are fully
     * covered by the pattern's cells, counting the free centre as always called.
     */
    private static Set<String> completedLines(List<int[]> cells) {
        Set<String> covered = new HashSet<>();
        for (int[] rc : cells) {
            covered.add(rc[0] + "," + rc[1]);
        }
        covered.add("2,2"); // the free centre is called by definition

        Set<String> complete = new HashSet<>();
        for (int r = 0; r < 5; r++) {
            if (coversRow(covered, r)) {
                complete.add("row" + r);
            }
        }
        for (int c = 0; c < 5; c++) {
            if (coversCol(covered, c)) {
                complete.add("col" + c);
            }
        }
        if (coversDiag(covered, 0, 1)) {
            complete.add("diag\\");
        }
        if (coversDiag(covered, 4, -1)) {
            complete.add("diag/");
        }
        return complete;
    }

    private static boolean coversRow(Set<String> covered, int row) {
        for (int c = 0; c < 5; c++) {
            if (!covered.contains(row + "," + c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean coversCol(Set<String> covered, int col) {
        for (int r = 0; r < 5; r++) {
            if (!covered.contains(r + "," + col)) {
                return false;
            }
        }
        return true;
    }

    private static boolean coversDiag(Set<String> covered, int startCol, int colStep) {
        for (int i = 0; i < 5; i++) {
            if (!covered.contains(i + "," + (startCol + i * colStep))) {
                return false;
            }
        }
        return true;
    }

    /**
     * THREE_SMALL_T's demo is two disconnected small T's plus a separate full line, so it
     * breaks into three connected groups. It used to be a single diagonal smear.
     */
    @Test
    @DisplayName("the three-shape demo really contains three separate components")
    void threeShapePatternsHaveThreeComponents() {
        for (String code : List.of("THREE_SMALL_T")) {
            List<int[]> cells = WinningPatternGeometry.cells(code);
            assertEquals(3, componentCount(cells), code + " must be three disconnected components");
        }
    }

    /**
     * THREE_SMALL_CROSSES is two full five-cell plus signs that overlap a full line (they share
     * cells with it, so the whole pattern is one connected shape). It used to be a diamond ring.
     */
    @Test
    @DisplayName("THREE_SMALL_CROSSES completes one line and two full plus signs")
    void threeSmallCrossesIsALineWithTwoPluses() {
        List<int[]> cells = WinningPatternGeometry.cells("THREE_SMALL_CROSSES");
        assertEquals(Set.of("row2"), completedLines(cells), "exactly one completed line");
        assertEquals(13, cells.size(), "two 5-cell pluses joined by the shared line");
        assertTrue(isFullPlus(cells, 1, 1), "plus sign centred at (1,1)");
        assertTrue(isFullPlus(cells, 3, 3), "plus sign centred at (3,3)");
    }

    /** The five cells of a plus sign centred at (row, col) are all part of the pattern. */
    private static boolean isFullPlus(List<int[]> cells, int row, int col) {
        for (int[] rc : new int[][] { { row, col }, { row - 1, col }, { row + 1, col },
                { row, col - 1 }, { row, col + 1 } }) {
            boolean found = false;
            for (int[] cell : cells) {
                if (cell[0] == rc[0] && cell[1] == rc[1]) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static int componentCount(List<int[]> cells) {
        boolean[][] seen = new boolean[5][5];
        int components = 0;
        for (int[] start : cells) {
            if (seen[start[0]][start[1]]) {
                continue;
            }
            components++;
            Deque<int[]> stack = new ArrayDeque<>();
            stack.push(start);
            seen[start[0]][start[1]] = true;
            while (!stack.isEmpty()) {
                int[] cur = stack.pop();
                for (int[] next : new int[][] { { cur[0] + 1, cur[1] }, { cur[0] - 1, cur[1] },
                        { cur[0], cur[1] + 1 }, { cur[0], cur[1] - 1 } }) {
                    if (next[0] < 0 || next[0] > 4 || next[1] < 0 || next[1] > 4) {
                        continue;
                    }
                    if (seen[next[0]][next[1]]) {
                        continue;
                    }
                    boolean required = false;
                    for (int[] cell : cells) {
                        if (cell[0] == next[0] && cell[1] == next[1]) {
                            required = true;
                            break;
                        }
                    }
                    if (required) {
                        seen[next[0]][next[1]] = true;
                        stack.push(next);
                    }
                }
            }
        }
        return components;
    }

    @Test
    @DisplayName("HALF_HOUSE starts as the top three rows; FULL_HOUSE-style patterns use every cell")
    void sanityOnNamedPatterns() {
        int[][] card = fullCard();
        assertEquals(15, WinningPatternGeometry.cells("HALF_HOUSE").size());
        assertEquals(15, WinningPatternGeometry.cells("THREE_LINES_NO_FREE_DISJOINT").size());
        assertEquals(16, WinningPatternGeometry.cells("FOUR_SQUARES").size());
    }

    /**
     * HALF_HOUSE can be won eight ways: three rows above or below a free band, three columns to
     * the left or right of one, or either side of either diagonal. Every layout is exactly fifteen
     * cells including the free centre, wins when complete, and is broken by dropping any single
     * real cell — and only HALF_HOUSE has these alternatives, so a code's variant list can never
     * leak into the single-shape patterns.
     */
    @Test
    @DisplayName("HALF_HOUSE wins through any of its eight half-card layouts")
    void halfHouseHasEightLayoutsThatAllWin() {
        int[][] card = fullCard();
        List<List<int[]>> layouts = WinningPatternGeometry.variants("HALF_HOUSE");
        assertEquals(8, layouts.size(), "HALF_HOUSE layouts");
        assertEquals(shape(WinningPatternGeometry.cells("HALF_HOUSE")), shape(layouts.get(0)),
                "the primary layout must stay first");

        for (List<int[]> layout : layouts) {
            String label = "layout " + layout;
            assertEquals(15, layout.size(), label + " cell count");
            assertTrue(layout.stream().anyMatch(cell -> cell[0] == 2 && cell[1] == 2),
                    label + " includes the free centre");
            assertTrue(engine.validateBingo(card, calledCells(card, layout), "HALF_HOUSE"),
                    label + " should win when complete");

            for (int[] drop : layout) {
                if (drop[0] == 2 && drop[1] == 2) continue; // free centre is never required
                List<int[]> without = new ArrayList<>(layout);
                without.remove(drop);
                assertFalse(engine.validateBingo(card, calledCells(card, without), "HALF_HOUSE"),
                        label + " must not win without cell " + drop[0] + "," + drop[1]);
            }
        }
    }

    @Test
    @DisplayName("single-shape codes keep exactly one layout, unknown codes none")
    void variantsForSingleShapeAndUnknownCodes() {
        assertEquals(1, WinningPatternGeometry.variants("FOUR_SQUARES").size(),
                "single-shape codes return only their grid");
        assertEquals(shape(WinningPatternGeometry.cells("FOUR_SQUARES")),
                shape(WinningPatternGeometry.variants("FOUR_SQUARES").get(0)),
                "the only variant is the grid itself");
        assertTrue(WinningPatternGeometry.variants("FULL_HOUSE").isEmpty(),
                "non-grid codes have no layouts");
        assertTrue(WinningPatternGeometry.variants(null).isEmpty(),
                "null has no layouts");
    }

    /** Coordinate lists as a comparable string; List<int[]> equality is reference-based. */
    private static String shape(List<int[]> cells) {
        StringBuilder out = new StringBuilder();
        cells.stream()
                .sorted((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1])
                .forEach(cell -> out.append(cell[0]).append(',').append(cell[1]).append(' '));
        return out.toString();
    }
}
