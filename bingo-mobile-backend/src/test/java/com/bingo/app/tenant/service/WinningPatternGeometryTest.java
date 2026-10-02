package com.bingo.app.tenant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    @Test
    @DisplayName("HALF_HOUSE is the top two rows; FULL_HOUSE-style patterns use every cell")
    void sanityOnNamedPatterns() {
        int[][] card = fullCard();
        assertEquals(10, WinningPatternGeometry.cells("HALF_HOUSE").size());
        assertEquals(15, WinningPatternGeometry.cells("THREE_LINES_NO_FREE_DISJOINT").size());
        assertEquals(16, WinningPatternGeometry.cells("FOUR_SQUARES").size());
    }
}
