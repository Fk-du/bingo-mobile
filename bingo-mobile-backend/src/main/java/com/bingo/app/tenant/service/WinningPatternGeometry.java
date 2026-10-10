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
 * <p>These codes are validated by {@link GameEngineService#validateBingo} through
 * {@link BingoPatternRules}, which treats each grid as the demonstration of a semantic
 * family: any arrangement the code's name describes wins, not just the exact cells below.
 * A code may declare several alternative layouts (see {@link #variants}); the pattern
 * wins as soon as any one layout is complete.
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
        GRIDS.put("TWO_LINES_TWO_SQUARES", "*****" + "**..." + "....." + "**..." + "*****");
        GRIDS.put("TWO_LINES_TWO_SEP_SQUARES", "**.**" + "**.**" + "*...*" + "*...*" + "*...*");
        GRIDS.put("THREE_SQUARES_FOUR_DOTS", "**.**" + "**.**" + "..*.*" + "**.*." + "**..*");

        // --- T shapes and crosses ----------------------------------------------------
        GRIDS.put("LARGE_T_TWO_LINES", "*****" + "*****" + "..*.." + "..*.." + "*****");
        GRIDS.put("LARGE_T_THREE_LINES", "*****" + "*****" + "..*.." + "*****" + "*****");
        GRIDS.put("TWO_SMALL_T_PLUS_LINE", "***.*" + ".*..*" + "....*" + ".*..*" + "***.*");
        GRIDS.put("LARGE_CROSS_TWO_SQUARES", "***.." + "***.." + "*****" + "..***" + "..***");
        GRIDS.put("TWO_SMALL_CROSSES_PLUS_LINE", "*..*." + ".****" + ".***." + "****." + ".*..*");

        // --- Half card ---------------------------------------------------------------
        GRIDS.put("HALF_HOUSE", "*****" + "*****" + "*****" + "....." + ".....");
    }

    /**
     * Alternative layouts for patterns that can be won through more than one shape. The
     * first entry is always the layout declared in {@link #GRIDS}, so {@link #cells} and
     * anything keyed off the primary layout (previews, cell counts) stay unchanged.
     *
     * <p>HALF_HOUSE is half of the card in any of eight ways: three rows above or below a
     * free band, three columns to the left or right of one, or either side of either
     * diagonal. Every layout is exactly fifteen cells and includes the free centre.
     */
    private static final Map<String, List<String>> VARIANTS = new LinkedHashMap<>();

    static {
        VARIANTS.put("HALF_HOUSE", List.of(
                "*****" + "*****" + "*****" + "....." + ".....",   // top three rows
                "....." + "....." + "*****" + "*****" + "*****",   // bottom three rows
                "***.." + "***.." + "***.." + "***.." + "***..",   // left three columns
                "..***" + "..***" + "..***" + "..***" + "..***",   // right three columns
                "*...." + "**..." + "***.." + "****." + "*****",   // main diagonal, lower-left half
                "*****" + ".****" + "..***" + "...**" + "....*",   // main diagonal, upper-right half
                "....*" + "...**" + "..***" + ".****" + "*****",   // anti-diagonal, lower-right half
                "*****" + "****." + "***.." + "**..." + "*...."    // anti-diagonal, upper-left half
        ));
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
        return cells(code).stream().anyMatch(cell -> cell[0] == 2 && cell[1] == 2);
    }

    /**
     * The cells that make up the pattern, or an empty list when the code is not one of the
     * grid-defined patterns. Multi-shape codes report their primary layout; use
     * {@link #variants} to test every layout a pattern wins through.
     */
    public static List<int[]> cells(String code) {
        String grid = code != null ? GRIDS.get(code) : null;
        return grid == null ? Collections.emptyList() : toCells(grid);
    }

    /**
     * Every layout that satisfies the pattern, primary layout first. A single-shape code
     * returns exactly the cells of {@link #cells}; codes listed in {@link #VARIANTS}
     * return all of their layouts. Unknown or non-grid codes return an empty list.
     */
    public static List<List<int[]>> variants(String code) {
        List<String> grids = code != null ? VARIANTS.get(code) : null;
        if (grids == null) {
            List<int[]> single = cells(code);
            return single.isEmpty() ? List.of() : List.of(single);
        }
        List<List<int[]>> layouts = new ArrayList<>();
        for (String grid : grids) {
            layouts.add(toCells(grid));
        }
        return layouts;
    }

    private static List<int[]> toCells(String grid) {
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
