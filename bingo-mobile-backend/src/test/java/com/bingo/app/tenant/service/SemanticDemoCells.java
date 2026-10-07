package com.bingo.app.tenant.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helper for the demo cells a pattern settles on. Most codes are won exactly by
 * their {@link WinningPatternGeometry} demo grid, but a few grids are so over-complete
 * that no single-cell-short copy of the demo is a loss (LARGE_T_THREE_LINES completes a
 * full seven lines, so any one of its cells can vanish and the family is still met).
 * The near-miss tests must instead drop a cell from a tighter winning arrangement, so
 * this exposes the overriding cell set for those codes.
 */
final class SemanticDemoCells {

    private SemanticDemoCells() {
    }

    /** Cells whose full call wins the pattern, overridden where the demo is over-complete. */
    static List<int[]> winningCells(String code) {
        if ("LARGE_T_THREE_LINES".equals(code)) {
            return minimalLargeTThree();
        }
        return WinningPatternGeometry.cells(code);
    }

    /**
     * A tight three-extras large T: rows 0, 1 and 3, column 0, and the (4,2) stem cell.
     * Column 2 then completes through the free centre, and the anti-diagonal through
     * (0,4),(1,3),(2,2),(3,1),(4,0). Losing (4,0) takes a whole extra line away, so the
     * set is one cell short of a win and the engine can prove the loss.
     */
    private static List<int[]> minimalLargeTThree() {
        List<int[]> cells = new ArrayList<>();
        addRow(cells, 0);
        addRow(cells, 1);
        addRow(cells, 3);
        cells.add(new int[]{2, 0});
        cells.add(new int[]{4, 0});
        cells.add(new int[]{4, 2});
        return cells;
    }

    private static void addRow(List<int[]> cells, int r) {
        for (int c = 0; c < 5; c++) {
            cells.add(new int[]{r, c});
        }
    }
}