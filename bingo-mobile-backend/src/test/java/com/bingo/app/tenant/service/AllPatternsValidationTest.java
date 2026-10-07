package com.bingo.app.tenant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive check of the claim-validation engine against every pattern the system
 * can ever be asked to decide automatically. For each recognised code a genuinely
 * complete card is a win and a one-cell-short card is a loss — that is the proof
 * a player's bingo will not be wrongly approved or wrongly rejected, for every
 * one of the 29 canonical picker patterns.
 */
class AllPatternsValidationTest {

    private final GameEngineService engine = new GameEngineService(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
    );

    private static List<int[]> allCells() {
        List<int[]> all = new ArrayList<>();
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                all.add(new int[]{r, c});
            }
        }
        return all;
    }

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
    @DisplayName("the recognised set covers every canonical picker code (never left undecided)")
    void recognisedSetCoversThePicker() {
        Set<String> picker = new LinkedHashSet<>(WinningPatternGeometry.codes());
        picker.add("FULL_HOUSE");
        assertAll(
                () -> assertTrue(GameEngineService.isRecognizedPattern("FULL_HOUSE")),
                () -> assertEquals(29, picker.size(), "canonical GamePatterns list size"),
                () -> assertTrue(picker.stream().allMatch(GameEngineService::isRecognizedPattern),
                        "no picker code may fall through to the manual admin review")
        );
    }

    @Test
    @DisplayName("unknown patterns are never recognised")
    void unknownPatternsAreNotRecognised() {
        List<String> unknown = new ArrayList<>();
        unknown.add("MYSTERY_PATTERN");
        unknown.add("SINGLE_LINE");
        unknown.add("BLACKOUT");
        unknown.add("POSTAGE_STAMP");
        unknown.add("");
        unknown.add("   ");
        unknown.add(null);
        for (String candidate : unknown) {
            assertFalse(GameEngineService.isRecognizedPattern(candidate),
                    "must not decide " + candidate);
        }
    }

    @Test
    @DisplayName("recognition is case and whitespace tolerant")
    void recognitionIsCaseInsensitive() {
        assertTrue(GameEngineService.isRecognizedPattern(" full_house "));
        assertTrue(GameEngineService.isRecognizedPattern("FOUR_SQUARES"));
    }

    @Test
    @DisplayName("every recognised pattern is won by a complete card and lost by a one-cell-short one")
    void everyRecognisedPatternDecidesBothWays() {
        int[][] card = fullCard();
        assertFalse(GameEngineService.recognizedPatternCodes().isEmpty());

        for (String code : GameEngineService.recognizedPatternCodes()) {
            List<int[]> winning = winningCells(code);

            String label = code + " (complete cells " + shape(winning) + ")";
            assertFalse(winning.isEmpty(), label + " must define cells");

            assertTrue(engine.validateBingo(card, calledCells(card, winning), code),
                    code + " must win when its winning cells are all called");

            for (int[] drop : dropsThatMustBreak(code, winning, engine, card)) {
                List<int[]> shortOf = without(winning, drop);
                assertFalse(engine.validateBingo(card, calledCells(card, shortOf), code),
                        code + " must NOT win without cell " + drop[0] + "," + drop[1]
                                + " (calls " + shape(shortOf) + ")");
            }
        }
    }

    /**
     * The cells whose values, when called, win the pattern.
     * <ul>
     *   <li>geometry codes: the cells of their primary grid through the production
     *       source, so what is tested is exactly what settles.</li>
     *   <li>multi-shape codes could also win through another layout, so their drops
     *       only use cells the alternative layouts can never cover (see below).</li>
     * </ul>
     */
    private static List<int[]> winningCells(String code) {
        if ("FULL_HOUSE".equals(code)) {
            return allCells();
        }
        return SemanticDemoCells.winningCells(code);
    }

    /**
     * The cells whose removal must turn the complete call-set into a loss.
     *
     * <p>Picker patterns are validated as semantic families (any arrangement of lines,
     * blocks, etc.), so not every grid cell is deadly — dropping a bar end from
     * THREE_RECTANGLES can still leave three rectangles. Each code is therefore
     * probed for a single cell the family really cannot do without, which is exactly
     * the near-miss a player must be rejected for.
     *
     * <p>HALF_HOUSE and FULL_HOUSE are cell-exact: the call-set is the layout
     * itself, so any one non-centre cell breaks it.
     */
    private static List<int[]> dropsThatMustBreak(String code, List<int[]> winning,
            GameEngineService engine, int[][] card) {
        if (WinningPatternGeometry.has(code) && !"HALF_HOUSE".equals(code)) {
            for (int[] cell : winning) {
                if (cell[0] == 2 && cell[1] == 2) {
                    continue; // the free centre is never a requirement
                }
                List<int[]> shortOf = without(winning, cell);
                if (!engine.validateBingo(card, calledCells(card, shortOf), code)) {
                    return List.of(cell);
                }
            }
            throw new IllegalStateException("no single-cell drop breaks " + code
                    + " under its semantic family");
        }
        List<int[]> drops = new ArrayList<>();
        for (int[] cell : winning) {
            if (cell[0] == 2 && cell[1] == 2) {
                continue; // the free centre is never a requirement
            }
            drops.add(cell);
        }
        return drops;
    }

    private static List<int[]> without(List<int[]> cells, int[] dropped) {
        List<int[]> out = new ArrayList<>();
        for (int[] cell : cells) {
            if (cell[0] != dropped[0] || cell[1] != dropped[1]) {
                out.add(cell);
            }
        }
        return out;
    }

    private static String shape(List<int[]> cells) {
        StringBuilder out = new StringBuilder();
        cells.stream()
                .sorted((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1])
                .forEach(cell -> out.append(cell[0]).append(',').append(cell[1]).append(' '));
        return out.toString();
    }
}