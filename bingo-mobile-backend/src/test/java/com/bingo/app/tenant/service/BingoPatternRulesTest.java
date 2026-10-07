package com.bingo.app.tenant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grids in the picker are demonstration layouts; settlement accepts every arrangement
 * a pattern's name describes. These tests prove the semantic families really are open by
 * winning each family with an arrangement that is <em>not</em> the demo grid.
 */
class BingoPatternRulesTest {

    private int[][] card() {
        int n = 1;
        int[][] card = new int[5][5];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                card[r][c] = (r == 2 && c == 2) ? 0 : n++;
            }
        }
        return card;
    }

    private static Set<Integer> called(int[][] card, Set<String> cells) {
        Set<Integer> called = new HashSet<>();
        for (String rc : cells) {
            int[] p = parts(rc);
            called.add(card[p[0]][p[1]]);
        }
        return called;
    }

    private void assertFamilyWins(String code, Set<String> arrangement) {
        int[][] card = card();
        assertTrue(BingoPatternRules.wins(card, called(card, arrangement), code),
                code + " must win on " + sorted(arrangement) + " (demo-oblivious family)");
    }

    // Line families: any N complete lines, any mix of rows/cols/diagonals -------------
    @Test
    @DisplayName("line ladders win on any N lines, not the demo's")
    void lineLaddersWinOnAnyLines() {
        assertAll(
                () -> assertFamilyWins("FOUR_LINES", rows(1, 2, 3, 4)),
                () -> assertFamilyWins("FIVE_LINES", cols(0, 1, 2, 3, 4)),
                () -> assertFamilyWins("SIX_LINES", union(rows(0, 1, 2, 3, 4), cols(0))),
                () -> assertFamilyWins("SEVEN_LINES", union(rows(0, 1, 3, 4), cols(0, 2), cols(4))),
                () -> assertFamilyWins("EIGHT_LINES", union(rows(0, 1, 2, 3, 4), cols(0, 1, 2))),
                () -> assertFamilyWins("THREE_LINES_ONE_DIAG", union(rows(0, 1, 4), diag("/"))),
                () -> assertFamilyWins("FOUR_LINES_TOUCH_FREE", union(rows(2), cols(2), diag("\\"), diag("/"))),
                () -> assertFamilyWins("THREE_LINES_NO_FREE_DISJOINT", union(rows(0, 4), cols(0))),
                () -> assertFamilyWins("FOUR_LINES_NO_FREE_DISJOINT", union(rows(0, 4), cols(0, 4))),
                () -> assertFamilyWins("FOUR_LINES_NO_FREE", union(rows(0, 4), cols(0, 4))),
                () -> assertFamilyWins("FIVE_LINES_NO_FREE", union(rows(0, 1, 3, 4), cols(0, 4))),
                () -> assertFamilyWins("TWO_TOUCH_TWO_NO_TOUCH", union(rows(2), cols(2), rows(0, 4))),
                () -> assertFamilyWins("TWO_VERT_TWO_HORIZ", union(rows(1, 2), cols(1, 3))),
                () -> assertFamilyWins("TWO_VERT_TWO_HORIZ_ONE_DIAG", union(rows(1, 2), cols(1, 3), diag("\\"))),
                () -> assertFamilyWins("TWO_VERT_THREE_HORIZ", union(rows(1, 2, 3), cols(0, 4))),
                () -> assertFamilyWins("TWO_HORIZ_TWO_VERT_TWO_DIAG", union(rows(0, 4), cols(0, 4), diag("\\"), diag("/")))
        );
    }

    // Shape families: any N copies of the shape anywhere ------------------------------
    @Test
    @DisplayName("square/rectangle families win on placements the demo does not use")
    void shapeFamiliesWinOnAnyArrangement() {
        assertAll(
                // Four disjoint 2x2 blocks, not the demo's corners.
                () -> assertFamilyWins("FOUR_SQUARES", union(block(0, 1), block(0, 3), block(2, 0), block(3, 2))),
                // Two lines plus two blocks, blocks outside the lines.
                () -> assertFamilyWins("TWO_LINES_TWO_SQUARES", union(cols(0, 4), block(0, 1), block(3, 1))),
                // Blocks pairwise disjoint from each other (lines may cross them).
                () -> assertFamilyWins("TWO_LINES_TWO_SEP_SQUARES", union(rows(0, 4), block(1, 0), block(3, 2))),
                // Three squares and four free dots, dots away from any block.
                () -> assertFamilyWins("THREE_SQUARES_FOUR_DOTS",
                        union(block(0, 0), block(0, 3), block(2, 2), dots("0,2", "2,0", "4,2", "4,4"))),
                // Three vertical bars instead of the demo's horizontal ones.
                () -> assertFamilyWins("THREE_RECTANGLES",
                        union(colRange(0, 0, 2), colRange(2, 0, 2), colRange(4, 0, 2))),
                // Two rows as the lines, plus an isolated mid-row bar as an extra rectangle.
                () -> assertFamilyWins("TWO_LINES_TWO_RECTANGLES",
                        union(rows(0, 4), Set.of("2,1", "2,2", "2,3")))
        );
    }

    @Test
    @DisplayName("T and cross families win on placements the demo does not use")
    void tAndCrossFamiliesWinOnAnyArrangement() {
        assertAll(
                // Three small T's: vertical stems on the left/right edges, one horizontal on the bottom.
                () -> assertFamilyWins("THREE_SMALL_T",
                        Set.of("0,0", "1,0", "2,0", "1,1",    // stem-on-the-right T on the left
                                "0,4", "1,4", "2,4", "1,3",    // stem-on-the-left T on the right
                                "4,0", "4,1", "4,2", "3,1")),  // stem-up T along the bottom
                // Three full pluses, sharing cells the way the demo does but in other spots.
                () -> assertFamilyWins("THREE_SMALL_CROSSES",
                        union(plus(1, 1), plus(1, 3), plus(3, 1))),
                // Large T in any of its four orientations, plus the named extra lines
                // anywhere else: top bar (demo's), bottom bar, left bar, right bar.
                () -> assertFamilyWins("LARGE_T_TWO_LINES", union(rows(0), cols(2), rows(3, 4))),
                () -> assertFamilyWins("LARGE_T_TWO_LINES", union(rows(4), cols(2), rows(0, 1))),
                () -> assertFamilyWins("LARGE_T_TWO_LINES", union(cols(0), rows(2), rows(4), cols(1))),
                () -> assertFamilyWins("LARGE_T_TWO_LINES", union(cols(4), rows(2), rows(4), cols(0))),
                () -> assertFamilyWins("LARGE_T_THREE_LINES", union(rows(0), cols(2), rows(1, 3, 4))),
                () -> assertFamilyWins("LARGE_T_THREE_LINES", union(rows(4), cols(2), rows(0, 1, 3))),
                // Large cross plus two blocks somewhere other than the demo's corners.
                () -> assertFamilyWins("LARGE_CROSS_TWO_SQUARES", union(rows(2), cols(2), block(0, 0), block(0, 3))),
                // HALF_HOUSE wins through a non-primary variant (the right three columns).
                () -> assertFamilyWins("HALF_HOUSE", cols(2, 3, 4))
        );
    }

    // --- Arrangement builders ---------------------------------------------------------

    private static int[] parts(String rc) {
        String[] p = rc.split(",");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    }

    private static Set<String> rows(int... rows) {
        Set<String> cells = new HashSet<>();
        for (int r : rows) {
            for (int c = 0; c < 5; c++) {
                cells.add(r + "," + c);
            }
        }
        return cells;
    }

    private static Set<String> cols(int... cols) {
        Set<String> cells = new HashSet<>();
        for (int c : cols) {
            for (int r = 0; r < 5; r++) {
                cells.add(r + "," + c);
            }
        }
        return cells;
    }

    private static Set<String> colRange(int col, int r0, int r1) {
        Set<String> cells = new HashSet<>();
        for (int r = r0; r <= r1; r++) {
            cells.add(r + "," + col);
        }
        return cells;
    }

    private static Set<String> diag(String which) {
        Set<String> cells = new HashSet<>();
        if ("\\".equals(which)) {
            for (int i = 0; i < 5; i++) {
                cells.add(i + "," + i);
            }
        } else {
            for (int i = 0; i < 5; i++) {
                cells.add(i + "," + (4 - i));
            }
        }
        return cells;
    }

    /** The four cells of a 2x2 block whose top-left corner is (r,c). */
    private static Set<String> block(int r, int c) {
        Set<String> cells = new HashSet<>();
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                cells.add((r + dr) + "," + (c + dc));
            }
        }
        return cells;
    }

    /** The five cells of a plus sign centred at (r,c). */
    private static Set<String> plus(int r, int c) {
        Set<String> cells = new HashSet<>();
        cells.add(r + "," + c);
        cells.add((r - 1) + "," + c);
        cells.add((r + 1) + "," + c);
        cells.add(r + "," + (c - 1));
        cells.add(r + "," + (c + 1));
        return cells;
    }

    private static Set<String> dots(String... coords) {
        Set<String> cells = new HashSet<>();
        for (String rc : coords) {
            cells.add(rc);
        }
        return cells;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static Set<String> union(Set<String> a, Set<String> b, Set<String> c) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        out.addAll(c);
        return out;
    }

    private static Set<String> union(Set<String> a, Set<String> b, Set<String> c, Set<String> d) {
        Set<String> out = union(a, b, c);
        out.addAll(d);
        return out;
    }

    private static String sorted(Set<String> cells) {
        return cells.stream().sorted().reduce("", (x, y) -> x + y + " ");
    }
}