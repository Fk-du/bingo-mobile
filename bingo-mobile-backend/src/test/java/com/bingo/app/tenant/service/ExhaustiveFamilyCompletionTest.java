package com.bingo.app.tenant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive positive verification of the settlement engine: for each canonical
 * picker pattern, every possible way a player can complete the pattern is won, and
 * every one-cell-short copy of a truly minimal way is lost.
 *
 * <p>The engine ({@link GameEngineService#validateBingo}) is the only thing under
 * test. The enumeration and the {@link #predicate} family model below are written
 * from the pattern <em>names</em> (any N lines, any N blocks, ...), so a winning
 * layout the engine miscounts shows up here as a failure with the cells attached.
 *
 * <p>Cells are 25-bit masks (index {@code r*5+c}); the free centre is implicit and
 * never part of a mask. The centre keeps its own value on the card, but the engine
 * treats it as called on every claim, so masks never need it.
 */
class ExhaustiveFamilyCompletionTest {

    private final GameEngineService engine = new GameEngineService(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
    );

    // --- bit helpers -------------------------------------------------------------
    private static final int CENTRE = 1 << 12;
    private static final int ALL = (1 << 25) - 1;
    private static final int NON_CENTRE = ALL & ~CENTRE;
    private static final int[] CENTRE_LINE_IDS = {2, 7, 10, 11};
    private static final int[] NON_CENTRE_LINE_IDS = {0, 1, 3, 4, 5, 6, 8, 9};

    private static final int[] LINE = new int[12];

    static {
        for (int r = 0; r < 5; r++) {
            int m = 0;
            for (int c = 0; c < 5; c++) {
                m |= 1 << (r * 5 + c);
            }
            LINE[r] = m;
        }
        for (int c = 0; c < 5; c++) {
            int m = 0;
            for (int r = 0; r < 5; r++) {
                m |= 1 << (r * 5 + c);
            }
            LINE[5 + c] = m;
        }
        int d1 = 0;
        for (int i = 0; i < 5; i++) {
            d1 |= 1 << (i * 5 + i);
        }
        LINE[10] = d1;
        int d2 = 0;
        for (int i = 0; i < 5; i++) {
            d2 |= 1 << (i * 5 + (4 - i));
        }
        LINE[11] = d2;
    }

    private static final List<Integer> BLOCKS = new ArrayList<>();

    static {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                BLOCKS.add((1 << (r * 5 + c)) | (1 << (r * 5 + c + 1))
                        | (1 << ((r + 1) * 5 + c)) | (1 << ((r + 1) * 5 + c + 1)));
            }
        }
    }

    /** Every plus sign centre, of the nine (rows/cols 1..3). */
    private static final List<Integer> PLUSES = new ArrayList<>();

    static {
        for (int r = 1; r < 4; r++) {
            for (int c = 1; c < 4; c++) {
                PLUSES.add((1 << (r * 5 + c)) | (1 << ((r - 1) * 5 + c)) | (1 << ((r + 1) * 5 + c))
                        | (1 << (r * 5 + c - 1)) | (1 << (r * 5 + c + 1)));
            }
        }
    }

    /** Every 4-cell small T placement (three in a line plus a stem). */
    private static final List<Integer> SMALL_TS = new ArrayList<>();

    static {
        for (int r = 0; r < 5; r++) {
            for (int c = 1; c < 4; c++) {
                addHorizontalT(r, c, r + 1, c);
                addHorizontalT(r, c, r - 1, c);
            }
        }
        for (int r = 1; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                addVerticalT(r, c, r, c + 1);
                addVerticalT(r, c, r, c - 1);
            }
        }
    }

    private static void addHorizontalT(int r, int c, int sr, int sc) {
        if (sr < 0 || sr > 4 || sc < 0 || sc > 4) {
            return;
        }
        int m = (1 << (r * 5 + c - 1)) | (1 << (r * 5 + c)) | (1 << (r * 5 + c + 1)) | (1 << (sr * 5 + sc));
        SMALL_TS.add(m);
    }

    private static void addVerticalT(int r, int c, int sr, int sc) {
        if (sr < 0 || sr > 4 || sc < 0 || sc > 4) {
            return;
        }
        int m = (1 << ((r - 1) * 5 + c)) | (1 << (r * 5 + c)) | (1 << ((r + 1) * 5 + c)) | (1 << (sr * 5 + sc));
        SMALL_TS.add(m);
    }

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

    private static List<Integer> called(int[][] card, int mask) {
        List<Integer> called = new ArrayList<>();
        int x = mask;
        while (x != 0) {
            int bit = Integer.lowestOneBit(x);
            int i = Integer.numberOfTrailingZeros(bit);
            called.add(card[i / 5][i % 5]);
            x ^= bit;
        }
        return called;
    }

    private static boolean marked(boolean[] m, int r, int c) {
        return (r == 2 && c == 2) || m[r * 5 + c];
    }

    private static boolean[] marks(int mask) {
        boolean[] m = new boolean[25];
        m[12] = true; // the free centre is always marked
        int x = mask;
        while (x != 0) {
            int bit = Integer.lowestOneBit(x);
            m[Integer.numberOfTrailingZeros(bit)] = true;
            x ^= bit;
        }
        return m;
    }

    // --- independent family model (from the pattern names) -----------------------

    private record LineCounts(int rows, int cols, int diags, int total, int centre, int noFree) {
    }

    private static LineCounts countLines(int mask) {
        boolean[] m = marks(mask);
        int rows = 0;
        for (int r = 0; r < 5; r++) {
            if (m[r * 5] && m[r * 5 + 1] && m[r * 5 + 2] && m[r * 5 + 3] && m[r * 5 + 4]) {
                rows++;
            }
        }
        int cols = 0;
        for (int c = 0; c < 5; c++) {
            if (m[c] && m[5 + c] && m[10 + c] && m[15 + c] && m[20 + c]) {
                cols++;
            }
        }
        int d1 = 1;
        for (int i = 0; i < 5; i++) {
            if (!m[i * 5 + i]) {
                d1 = 0;
            }
        }
        int d2 = 1;
        for (int i = 0; i < 5; i++) {
            if (!m[i * 5 + (4 - i)]) {
                d2 = 0;
            }
        }
        int diags = d1 + d2;
        int total = rows + cols + diags;
        int centre = 0;
        if (m[10] && m[11] && m[12] && m[13] && m[14]) {
            centre++;
        }
        if (m[2] && m[7] && m[12] && m[17] && m[22]) {
            centre++;
        }
        if (d1 == 1) {
            centre++;
        }
        if (d2 == 1) {
            centre++;
        }
        return new LineCounts(rows, cols, diags, total, centre, total - centre);
    }

    private static List<Integer> completeBlocks(int mask) {
        boolean[] m = marks(mask);
        List<Integer> blocks = new ArrayList<>();
        for (int b : BLOCKS) {
            boolean ok = true;
            int x = b;
            while (x != 0) {
                int bit = Integer.lowestOneBit(x);
                int i = Integer.numberOfTrailingZeros(bit);
                if (!m[i]) {
                    ok = false;
                    break;
                }
                x ^= bit;
            }
            if (ok) {
                blocks.add(b);
            }
        }
        return blocks;
    }

    private static boolean canPack(List<Integer> shapes, int need, int mask) {
        return packMore(shapes, need, mask | CENTRE, 0, 0, 0);
    }

    private static boolean packMore(List<Integer> shapes, int need, int mask, int index, int used, int picked) {
        if (picked >= need) {
            return true;
        }
        if (picked + (shapes.size() - index) < need) {
            return false;
        }
        for (int i = index; i < shapes.size(); i++) {
            int s = shapes.get(i);
            if ((mask & s) == s && (used & s) == 0
                    && packMore(shapes, need, mask, i + 1, used | s, picked + 1)) {
                return true;
            }
        }
        return false;
    }

    private static int dotCount(int mask) {
        List<Integer> blocks = completeBlocks(mask);
        int x = mask | CENTRE;
        int union = 0;
        for (int b : blocks) {
            union |= b;
        }
        int dots = Integer.bitCount(x & ~union);
        if ((union & CENTRE) != 0) {
            dots++;
        }
        return dots;
    }

    private static int crossCount(int mask) {
        boolean[] m = marks(mask);
        int crosses = 0;
        for (int r = 1; r < 4; r++) {
            for (int c = 1; c < 4; c++) {
                boolean ok = m[r * 5 + c] && m[(r - 1) * 5 + c] && m[(r + 1) * 5 + c]
                        && m[r * 5 + c - 1] && m[r * 5 + c + 1];
                if (ok) {
                    crosses++;
                }
            }
        }
        return crosses;
    }

    private static boolean largeT(int mask, int extraLines) {
        boolean[] m = marks(mask);
        boolean barTop = m[0] && m[1] && m[2] && m[3] && m[4]
                && m[2] && m[7] && m[12] && m[17] && m[22];
        boolean barBottom = m[20] && m[21] && m[22] && m[23] && m[24]
                && m[2] && m[7] && m[12] && m[17] && m[22];
        boolean barLeft = m[0] && m[5] && m[10] && m[15] && m[20]
                && m[10] && m[11] && m[12] && m[13] && m[14];
        boolean barRight = m[4] && m[9] && m[14] && m[19] && m[24]
                && m[10] && m[11] && m[12] && m[13] && m[14];
        if (!(barTop || barBottom || barLeft || barRight)) {
            return false;
        }
        return countLines(mask).total() - 2 >= extraLines;
    }

    private static boolean halfHouse(int mask) {
        for (List<int[]> layout : WinningPatternGeometry.variants("HALF_HOUSE")) {
            int m = mask;
            boolean complete = true;
            for (int[] cell : layout) {
                if (cell[0] == 2 && cell[1] == 2) {
                    continue;
                }
                if ((m & (1 << (cell[0] * 5 + cell[1]))) == 0) {
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

    /** Whether {@code mask} satisfies the pattern's family rule, independently of the engine. */
    private static boolean predicate(String code, int mask) {
        if (mask == 0) {
            return false;
        }
        if ("FULL_HOUSE".equals(code)) {
            return Integer.bitCount(mask) == 24;
        }
        if ("HALF_HOUSE".equals(code)) {
            return halfHouse(mask);
        }
        LineCounts l = countLines(mask);
        int squares = 0;
        int dots = 0;
        int crosses = 0;
        List<Integer> blocks = null;
        if (squaresFamily(code)) {
            blocks = completeBlocks(mask);
            squares = blocks.size();
        }
         if ("THREE_SQUARES_FOUR_DOTS".equals(code)) {
            dots = dotCount(mask);
        }
        if ("THREE_SMALL_CROSSES".equals(code)) {
            crosses = crossCount(mask);
        }
        return switch (code) {
            case "FOUR_LINES" -> l.total() >= 4;
            case "FIVE_LINES" -> l.total() >= 5;
            case "SIX_LINES" -> l.total() >= 6;
            case "SEVEN_LINES" -> l.total() >= 7;
            case "EIGHT_LINES" -> l.total() >= 8;
            case "THREE_LINES_ONE_DIAG" -> l.rows() + l.cols() >= 3 && l.diags() >= 1;
            case "FOUR_LINES_TOUCH_FREE" -> l.centre() >= 4;
            case "THREE_LINES_NO_FREE_DISJOINT" -> l.noFree() >= 3;
            case "FOUR_LINES_NO_FREE_DISJOINT", "FOUR_LINES_NO_FREE" -> l.noFree() >= 4;
            case "FIVE_LINES_NO_FREE" -> l.noFree() >= 5;
            case "TWO_TOUCH_TWO_NO_TOUCH" -> l.centre() >= 2 && l.noFree() >= 2;
            case "TWO_VERT_TWO_HORIZ" -> l.cols() >= 2 && l.rows() >= 2;
            case "TWO_VERT_TWO_HORIZ_ONE_DIAG" -> l.cols() >= 2 && l.rows() >= 2 && l.diags() >= 1;
            case "TWO_VERT_THREE_HORIZ" -> l.cols() >= 2 && l.rows() >= 3;
            case "TWO_HORIZ_TWO_VERT_TWO_DIAG" -> l.rows() >= 2 && l.cols() >= 2 && l.diags() >= 2;
            case "FOUR_SQUARES" -> canPack(blocks, 4, mask);
            case "TWO_LINES_TWO_SQUARES" -> l.total() >= 2 && squares >= 2;
            case "TWO_LINES_TWO_SEP_SQUARES" -> l.total() >= 2 && canPack(blocks, 2, mask);
            case "THREE_SQUARES_FOUR_DOTS" -> squares >= 3 && dots >= 4;
            case "LARGE_T_TWO_LINES" -> largeT(mask, 2);
            case "LARGE_T_THREE_LINES" -> largeT(mask, 3);
            case "THREE_SMALL_T" -> canPack(SMALL_TS, 3, mask);
            case "THREE_SMALL_CROSSES" -> crosses >= 3;
            case "LARGE_CROSS_TWO_SQUARES" -> {
                boolean[] m = marks(mask);
                boolean row2 = m[10] && m[11] && m[12] && m[13] && m[14];
                boolean col2 = m[2] && m[7] && m[12] && m[17] && m[22];
                yield row2 && col2 && squares >= 2;
            }
            default -> false;
        };
    }

    private static boolean squaresFamily(String code) {
        return switch (code) {
            case "FOUR_SQUARES", "TWO_LINES_TWO_SQUARES", "TWO_LINES_TWO_SEP_SQUARES",
                    "THREE_SQUARES_FOUR_DOTS", "LARGE_CROSS_TWO_SQUARES" -> true;
            default -> false;
        };
    }

    private static boolean certifiedMinimal(String code, int mask) {
        int x = mask;
        while (x != 0) {
            int bit = Integer.lowestOneBit(x);
            if (predicate(code, mask & ~bit)) {
                return false;
            }
            x ^= bit;
        }
        return true;
    }

    // --- per-family exhaustive enumeration of the ways ---------------------------

    private static List<int[]> combos(int n, int k) {
        List<int[]> out = new ArrayList<>();
        int[] cur = new int[k];
        combosRec(n, k, 0, 0, cur, out);
        return out;
    }

    private static void combosRec(int n, int k, int idx, int start, int[] cur, List<int[]> out) {
        if (idx == k) {
            out.add(cur.clone());
            return;
        }
        for (int i = start; i <= n - (k - idx); i++) {
            cur[idx] = i;
            combosRec(n, k, idx + 1, i + 1, cur, out);
        }
    }

    private static void add(Set<Integer> ways, int mask) {
        ways.add(mask & NON_CENTRE);
    }

    private static int unionLines(int... ids) {
        int m = 0;
        for (int id : ids) {
            m |= LINE[id];
        }
        return m;
    }

    private static void disjointCombos(List<Integer> shapes, int need, int index, int used,
            List<Integer> picked, List<List<Integer>> out) {
        if (picked.size() == need) {
            out.add(new ArrayList<>(picked));
            return;
        }
        for (int i = index; i <= shapes.size() - (need - picked.size()); i++) {
            int s = shapes.get(i);
            if ((used & s) == 0) {
                picked.add(i);
                disjointCombos(shapes, need, i + 1, used | s, picked, out);
                picked.remove(picked.size() - 1);
            }
        }
    }

    private static LinkedHashSet<Integer> allWays(String code) {
        LinkedHashSet<Integer> ways = new LinkedHashSet<>();
        switch (code) {
            case "FOUR_LINES" -> addLineCombos(ways, 12, 4, null);
            case "FIVE_LINES" -> addLineCombos(ways, 12, 5, null);
            case "SIX_LINES" -> addLineCombos(ways, 12, 6, null);
            case "SEVEN_LINES" -> addLineCombos(ways, 12, 7, null);
            case "EIGHT_LINES" -> addLineCombos(ways, 12, 8, null);
            case "THREE_LINES_ONE_DIAG" -> {
                for (int[] rc : combos(10, 3)) {
                    for (int d : new int[]{10, 11}) {
                        int[] ids = new int[4];
                        System.arraycopy(rc, 0, ids, 0, 3);
                        ids[3] = d;
                        add(ways, unionLines(ids));
                    }
                }
            }
            case "FOUR_LINES_TOUCH_FREE" -> add(ways, unionLines(CENTRE_LINE_IDS));
            case "THREE_LINES_NO_FREE_DISJOINT" -> addLineCombos(ways, NON_CENTRE_LINE_IDS.length, 3, NON_CENTRE_LINE_IDS);
            case "FOUR_LINES_NO_FREE_DISJOINT", "FOUR_LINES_NO_FREE" ->
                    addLineCombos(ways, NON_CENTRE_LINE_IDS.length, 4, NON_CENTRE_LINE_IDS);
            case "FIVE_LINES_NO_FREE" -> addLineCombos(ways, NON_CENTRE_LINE_IDS.length, 5, NON_CENTRE_LINE_IDS);
            case "TWO_TOUCH_TWO_NO_TOUCH" -> {
                for (int[] t : combos(4, 2)) {
                    for (int[] n : combos(8, 2)) {
                        add(ways, unionLines(CENTRE_LINE_IDS[t[0]], CENTRE_LINE_IDS[t[1]],
                                NON_CENTRE_LINE_IDS[n[0]], NON_CENTRE_LINE_IDS[n[1]]));
                    }
                }
            }
            case "TWO_VERT_TWO_HORIZ" -> addRowColMix(ways, 2, 2, false);
            case "TWO_VERT_TWO_HORIZ_ONE_DIAG" -> addRowColMix(ways, 2, 2, true);
            case "TWO_VERT_THREE_HORIZ" -> addRowColMix(ways, 3, 2, false);
            case "TWO_HORIZ_TWO_VERT_TWO_DIAG" -> {
                for (int[] rs : combos(5, 2)) {
                    for (int[] cs : combos(5, 2)) {
                        add(ways, unionLines(rs[0], rs[1], 5 + cs[0], 5 + cs[1], 10, 11));
                    }
                }
            }
            case "FOUR_SQUARES" -> {
                List<List<Integer>> packs = new ArrayList<>();
                disjointCombos(BLOCKS, 4, 0, 0, new ArrayList<>(), packs);
                for (List<Integer> pack : packs) {
                    int m = 0;
                    for (int i : pack) {
                        m |= BLOCKS.get(i);
                    }
                    add(ways, m);
                }
            }
            case "TWO_LINES_TWO_SQUARES" -> {
                for (int[] lp : combos(12, 2)) {
                    for (int[] bp : combos(16, 2)) {
                        add(ways, unionLines(lp[0], lp[1]) | BLOCKS.get(bp[0]) | BLOCKS.get(bp[1]));
                    }
                }
            }
            case "TWO_LINES_TWO_SEP_SQUARES" -> {
                for (int[] lp : combos(12, 2)) {
                    List<List<Integer>> pairs = new ArrayList<>();
                    disjointCombos(BLOCKS, 2, 0, 0, new ArrayList<>(), pairs);
                    for (List<Integer> pair : pairs) {
                        add(ways, unionLines(lp[0], lp[1]) | BLOCKS.get(pair.get(0)) | BLOCKS.get(pair.get(1)));
                    }
                }
            }
            case "THREE_SQUARES_FOUR_DOTS" -> addThreeSquaresFourDots(ways);
            case "LARGE_T_TWO_LINES" -> addLargeT(ways, 2);
            case "LARGE_T_THREE_LINES" -> addLargeT(ways, 3);
            case "THREE_SMALL_T" -> {
                List<List<Integer>> packs = new ArrayList<>();
                disjointCombos(SMALL_TS, 3, 0, 0, new ArrayList<>(), packs);
                for (List<Integer> pack : packs) {
                    int m = 0;
                    for (int i : pack) {
                        m |= SMALL_TS.get(i);
                    }
                    add(ways, m);
                }
            }
            case "LARGE_CROSS_TWO_SQUARES" -> {
                for (int[] bp : combos(16, 2)) {
                    add(ways, unionLines(2, 7) | BLOCKS.get(bp[0]) | BLOCKS.get(bp[1]));
                }
            }
            case "THREE_SMALL_CROSSES" -> {
                for (int[] cp : combos(9, 3)) {
                    add(ways, PLUSES.get(cp[0]) | PLUSES.get(cp[1]) | PLUSES.get(cp[2]));
                }
            }
            case "HALF_HOUSE" -> {
                for (List<int[]> layout : WinningPatternGeometry.variants("HALF_HOUSE")) {
                    int m = 0;
                    for (int[] cell : layout) {
                        if (cell[0] == 2 && cell[1] == 2) {
                            continue;
                        }
                        m |= 1 << (cell[0] * 5 + cell[1]);
                    }
                    add(ways, m);
                }
            }
            case "FULL_HOUSE" -> add(ways, NON_CENTRE);
            default -> throw new IllegalStateException("no enumerator for " + code);
        }
        return ways;
    }

    private static void addLineCombos(Set<Integer> ways, int n, int k, int[] lineIds) {
        for (int[] combo : combos(n, k)) {
            int[] ids = new int[k];
            for (int i = 0; i < k; i++) {
                ids[i] = (lineIds == null) ? combo[i] : lineIds[combo[i]];
            }
            add(ways, unionLines(ids));
        }
    }

    private static void addRowColMix(Set<Integer> ways, int rows, int cols, boolean diag) {
        for (int[] rs : combos(5, rows)) {
            for (int[] cs : combos(5, cols)) {
                int[] ids = new int[rows + cols + (diag ? 1 : 0)];
                int i = 0;
                for (int r : rs) {
                    ids[i++] = r;
                }
                for (int c : cs) {
                    ids[i++] = 5 + c;
                }
                if (diag) {
                    ids[i] = 10;
                }
                add(ways, unionLines(ids));
            }
        }
    }

    private static void addLargeT(Set<Integer> ways, int extraLines) {
        int row0col2 = unionLines(0, 7);
        int row4col2 = unionLines(4, 7);
        int col0row2 = unionLines(2, 5);
        int col4row2 = unionLines(2, 9);
        for (int base : new int[]{row0col2, row4col2, col0row2, col4row2}) {
            for (int[] e : combos(12, extraLines)) {
                add(ways, base | unionLines(e));
            }
        }
    }

    private static void addThreeSquaresFourDots(Set<Integer> ways) {
        for (int[] bt : combos(16, 3)) {
            int unionB = BLOCKS.get(bt[0]) | BLOCKS.get(bt[1]) | BLOCKS.get(bt[2]);
            int complement = NON_CENTRE & ~unionB;
            int[] cand = new int[Integer.bitCount(complement)];
            int x = complement;
            int i = 0;
            while (x != 0) {
                cand[i++] = Integer.numberOfTrailingZeros(Integer.lowestOneBit(x));
                x &= x - 1;
            }
            if (cand.length >= 4) {
                fourDots(ways, cand, cand.length, 0, 0, 4, unionB);
            }
            fourDots(ways, cand, cand.length, 0, 0, 3, unionB);
        }
    }

    private static void fourDots(Set<Integer> ways, int[] cand, int n, int idx, int start, int k, int unionB) {
        int[] pick = new int[k];
        fourDotsPick(ways, cand, n, idx, start, k, unionB, pick);
    }

    private static void fourDotsPick(Set<Integer> ways, int[] cand, int n, int idx, int start, int k, int unionB, int[] pick) {
        if (idx == k) {
            int dots = 0;
            for (int j = 0; j < k; j++) {
                dots |= 1 << pick[j];
            }
            add(ways, unionB | dots);
            return;
        }
        for (int i = start; i <= n - (k - idx); i++) {
            pick[idx] = cand[i];
            fourDotsPick(ways, cand, n, idx + 1, i + 1, k, unionB, pick);
        }
    }

    private static boolean[][] disjointPairs(List<Integer> shapes, int size, boolean unused) {
        boolean[][] out = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                out[i][j] = (shapes.get(i) & shapes.get(j)) == 0;
            }
        }
        return out;
    }

    // --- the test ----------------------------------------------------------------

    @Test
    @DisplayName("every way of completing a canonical pattern is won, one-cell-short ways are lost")
    void everyCompletionWayDecidesCorrectly() {
        int[][] card = card();
        int totalKept = 0;
        int totalMinimal = 0;
        StringBuilder summary = new StringBuilder();
        for (String code : codes()) {
            LinkedHashSet<Integer> ways = allWays(code);
            int kept = 0;
            int minimal = 0;
            for (int m : ways) {
                if (!predicate(code, m)) {
                    continue;
                }
                kept++;
                assertTrue(engine.validateBingo(card, called(card, m), code),
                        code + " must win on " + shape(m) + " (" + called(card, m) + ")");
                if (certifiedMinimal(code, m)) {
                    minimal++;
                    int x = m;
                    while (x != 0) {
                        int bit = Integer.lowestOneBit(x);
                        assertFalse(engine.validateBingo(card, called(card, m & ~bit), code),
                                code + " must NOT win when one cell is missing from " + shape(m));
                        x ^= bit;
                    }
                }
            }
            assertTrue(kept > 0, code + " must have at least one enumerated way");
            totalKept += kept;
            totalMinimal += minimal;
            summary.append(String.format("%-34s enumerated=%-6d ways=%-6d minimal=%-6d%n",
                    code, ways.size(), kept, minimal));
        }
        summary.append(String.format("%-34s ways=%-6d minimal=%-6d%n", "TOTAL", totalKept, totalMinimal));
        System.out.println(summary);
    }

    private static List<String> codes() {
        return List.of(
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
                "HALF_HOUSE", "FULL_HOUSE"
        );
    }

    private static String shape(int mask) {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (r == 2 && c == 2) {
                    out.append('X');
                } else if ((mask & (1 << (r * 5 + c))) != 0) {
                    out.append('#');
                } else {
                    out.append('.');
                }
            }
            out.append('\n');
        }
        return out.toString();
    }
}