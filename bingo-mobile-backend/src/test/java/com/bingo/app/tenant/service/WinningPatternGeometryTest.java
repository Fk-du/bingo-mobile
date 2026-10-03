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
            "TWO_LINES_TWO_RECTANGLES", "THREE_SQUARES_FOUR_DOTS", "THREE_RECTANGLES",
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
    @DisplayName("each pattern fails when any single required cell is missing")
    void failsWhenOneCellMissing() {
        int[][] card = fullCard();
        for (String code : EXPECTED_CODES) {
            List<int[]> cells = WinningPatternGeometry.cells(code);
            for (int[] drop : cells) {
                if (drop[0] == 2 && drop[1] == 2) continue; // free centre is never required
                List<int[]> without = new ArrayList<>(cells);
                without.remove(drop);
                assertFalse(engine.validateBingo(card, calledCells(card, without), code),
                        code + " must not win without cell " + drop[0] + "," + drop[1]);
            }
        }
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
                new Expectation("TWO_LINES_TWO_SQUARES", 4),
                new Expectation("TWO_LINES_TWO_RECTANGLES", 2),
                new Expectation("THREE_RECTANGLES", 0),
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
     * THREE_SMALL_T and THREE_SMALL_CROSSES name shapes rather than lines, so they are guarded by
     * component count instead: each of the three shapes must be its own connected group. They used
     * to be a single diagonal smear and a diamond ring respectively.
     */
    @Test
    @DisplayName("the three-shape patterns really contain three separate shapes")
    void threeShapePatternsHaveThreeComponents() {
        for (String code : List.of("THREE_SMALL_T", "THREE_SMALL_CROSSES", "THREE_RECTANGLES")) {
            List<int[]> cells = WinningPatternGeometry.cells(code);
            assertEquals(3, componentCount(cells), code + " must be three disconnected shapes");
        }
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
    @DisplayName("HALF_HOUSE is the top three rows; FULL_HOUSE-style patterns use every cell")
    void sanityOnNamedPatterns() {
        int[][] card = fullCard();
        assertEquals(15, WinningPatternGeometry.cells("HALF_HOUSE").size());
        assertEquals(15, WinningPatternGeometry.cells("THREE_LINES_NO_FREE_DISJOINT").size());
        assertEquals(16, WinningPatternGeometry.cells("FOUR_SQUARES").size());
    }
}
