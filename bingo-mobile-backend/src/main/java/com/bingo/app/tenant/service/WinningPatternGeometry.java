package com.bingo.app.tenant.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cell layouts for the 5x5 winning patterns offered in the admin game-creation picker.
 *
 * <p>Each entry is a 5x5 grid written as five rows of five characters, top row first:
 * {@code '*'} marks a cell the card must have called, {@code '.'} marks a cell that is
 * irrelevant to the pattern. The centre cell (row 2, column 2) is free on every card, so
 * when a pattern includes it the cell is always considered complete.
 *
 * <p>These codes are validated by {@link GameEngineService#validateBingo} through the same
 * "every listed cell must be called" rule, which is why they do not appear in the older,
 * hand-rolled {@code if ("X_SHAPE".equals(pattern))} branches.
 */
public final class WinningPatternGeometry {

    private WinningPatternGeometry() {
    }

    private static final Map<String, String> GRIDS = new LinkedHashMap<>();

    static {
        // --- Line ladders -----------------------------------------------------------
        GRIDS.put("FOUR_LINES", "*****" + "*****" + "*...*" + "*...*" + "*...*");
        GRIDS.put("FIVE_LINES", "*****" + "*****" + "*****" + "**..." + "*....");
        GRIDS.put("SIX_LINES", "*****" + "*****" + "*****" + "**..." + "**...");
        GRIDS.put("SEVEN_LINES", "*****" + "*****" + "*.*.." + "*****" + "*.*.*");
        GRIDS.put("EIGHT_LINES", "*****" + "*****" + "*.*.*" + "*****" + "*.*.*");
        GRIDS.put("THREE_LINES_ONE_DIAG", "*****" + "*****" + "..*.." + "...*." + "*****");
        GRIDS.put("FOUR_LINES_TOUCH_FREE", "*.*.*" + ".***." + "*****" + ".***." + "*.*.*");

        // --- Line ladders that never rely on the free centre ------------------------
        GRIDS.put("THREE_LINES_NO_FREE_DISJOINT", "*****" + "*****" + "....." + "....." + "*****");
        GRIDS.put("FOUR_LINES_NO_FREE_DISJOINT", "*****" + "*...*" + "*...*" + "*...*" + "*****");
        GRIDS.put("FOUR_LINES_NO_FREE", "*****" + "*****" + "*...." + "*...." + "*****");
        GRIDS.put("FIVE_LINES_NO_FREE", "*****" + "*****" + "*...*" + "*...*" + "*****");
        GRIDS.put("TWO_TOUCH_TWO_NO_TOUCH", "*****" + "..*.." + "*****" + "..*.." + "*****");

        // --- Vertical / horizontal / diagonal mixes ---------------------------------
        GRIDS.put("TWO_VERT_TWO_HORIZ", "*****" + "**..." + "**..." + "**..." + "*****");
        GRIDS.put("TWO_VERT_TWO_HORIZ_ONE_DIAG", "*****" + "**..*" + "*.*.*" + "*..**" + "*****");
        GRIDS.put("TWO_VERT_THREE_HORIZ", "*****" + "**..." + "**..." + "*****" + "*****");
        GRIDS.put("TWO_HORIZ_TWO_VERT_TWO_DIAG", "*****" + "**.**" + "*.*.*" + "**.**" + "*****");

        // --- Squares, rectangles and dots -------------------------------------------
        GRIDS.put("FOUR_SQUARES", "**.**" + "**.**" + "....." + "**.**" + "**.**");
        GRIDS.put("TWO_LINES_TWO_SQUARES", "*****" + "*...." + "**.**" + "**.**" + "*****");
        GRIDS.put("TWO_LINES_TWO_SEP_SQUARES", "*...*" + "**.*." + "*.**." + "*.**." + "*..**");
        GRIDS.put("TWO_LINES_TWO_RECTANGLES", "*****" + "....." + "*...*" + "*...*" + "*****");
        GRIDS.put("THREE_SQUARES_FOUR_DOTS", "*****" + "**.**" + "*...*" + "**..." + "**.*.");
        GRIDS.put("THREE_RECTANGLES", "****." + "....." + "****." + "....." + "****.");

        // --- T shapes and crosses ----------------------------------------------------
        GRIDS.put("LARGE_T_TWO_LINES", "*****" + "*****" + "..*.." + "..*.." + "*****");
        GRIDS.put("LARGE_T_THREE_LINES", "*****" + "*****" + "..*.." + "*****" + "*****");
        GRIDS.put("THREE_SMALL_T", "***.." + ".*..." + "*...." + "**.*." + "*.***");
        GRIDS.put("LARGE_CROSS_TWO_SQUARES", "**..." + "**..." + "*****" + "..***" + "..***");
        GRIDS.put("THREE_SMALL_CROSSES", ".***." + "*.*.." + "**..." + "*.*.." + ".***.");

        // --- Half card ---------------------------------------------------------------
        GRIDS.put("HALF_HOUSE", "*****" + "*****" + "*****" + "....." + ".....");
    }

    /** Every code defined here, in the order they were declared. */
    public static Set<String> codes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(GRIDS.keySet()));
    }

    public static boolean has(String code) {
        return code != null && GRIDS.containsKey(code);
    }

    /** True when the pattern includes the free centre cell. */
    public static boolean usesFreeCentre(String code) {
        return cells(code).contains(new int[]{2, 2});
    }

    /**
     * The cells that make up the pattern, or an empty list when the code is not one of the
     * grid-defined patterns.
     */
    public static List<int[]> cells(String code) {
        String grid = code != null ? GRIDS.get(code) : null;
        if (grid == null) {
            return Collections.emptyList();
        }
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (grid.charAt(row * 5 + col) == '*') {
                    cells.add(new int[]{row, col});
                }
            }
        }
        return cells;
    }
}
