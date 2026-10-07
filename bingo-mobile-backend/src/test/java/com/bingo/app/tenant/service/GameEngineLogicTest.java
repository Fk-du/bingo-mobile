package com.bingo.app.tenant.service;

import com.bingo.app.tenant.entity.GameCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for the bingo engine's money and win-detection rules.
 * No Spring context — runs in milliseconds.
 */
class GameEngineLogicTest {

    private final GameEngineService engine = new GameEngineService(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
    );

    // ===== cent-perfect equal splitting =====

    @Test
    @DisplayName("splitEvenly: exact division")
    void splitExact() {
        BigDecimal[] shares = engine.splitEvenly(new BigDecimal("18.00"), 2);
        assertAll(
                () -> assertEquals(new BigDecimal("9.00"), shares[0]),
                () -> assertEquals(new BigDecimal("9.00"), shares[1]),
                () -> assertEquals(0, new BigDecimal("18.00").compareTo(shares[0].add(shares[1])))
        );
    }

    @Test
    @DisplayName("splitEvenly: remainder absorbed by earlier winners, total conserved")
    void splitRounding() {
        BigDecimal[] shares = engine.splitEvenly(new BigDecimal("10.00"), 3);
        assertEquals(0, new BigDecimal("10.00").compareTo(shares[0].add(shares[1]).add(shares[2])));
        assertTrue(shares[0].compareTo(shares[2]) >= 0, "earlier winners absorb the rounding");
        assertEquals(new BigDecimal("3.34"), shares[0]);
        assertEquals(new BigDecimal("3.33"), shares[1]);
        assertEquals(new BigDecimal("3.33"), shares[2]);
    }

    @Test
    @DisplayName("splitEvenly: single cent among two winners")
    void splitTinyAmounts() {
        BigDecimal[] shares = engine.splitEvenly(new BigDecimal("0.01"), 2);
        assertEquals(0, new BigDecimal("0.01").compareTo(shares[0].add(shares[1])));
    }

    // ===== pattern validation (mirrors server rules; free centre counts as called) =====

    private int[][] fullCard() {
        int n = 1;
        int[][] card = new int[5][5];
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                card[r][c] = (r == 2 && c == 2) ? 0 : n++;
        return card;
    }

    private List<Integer> cells(int[][] card, int[][] coords) {
        return java.util.Arrays.stream(coords).map(rc -> card[rc[0]][rc[1]]).toList();
    }

    @Test
    @DisplayName("FULL_HOUSE: all 24 non-free numbers required")
    void fullHouse() {
        int[][] card = fullCard();
        List<Integer> all = cells(card, java.util.Arrays.stream(
                new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4},
                        {1, 0}, {1, 1}, {1, 2}, {1, 3}, {1, 4},
                        {2, 0}, {2, 1}, {2, 2}, {2, 3}, {2, 4},
                        {3, 0}, {3, 1}, {3, 2}, {3, 3}, {3, 4},
                        {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}}).toArray(int[][]::new));
        assertTrue(engine.validateBingo(card, all, "FULL_HOUSE"));
        assertFalse(engine.validateBingo(card, all.subList(0, 23), "FULL_HOUSE"));
    }

    @Test
    @DisplayName("a pattern outside the canonical set is never a win")
    void retiredAndUnknownPatternsNeverWin() {
        int[][] card = fullCard();
        List<Integer> all = cells(card, java.util.Arrays.stream(
                new int[][]{{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4},
                        {1, 0}, {1, 1}, {1, 2}, {1, 3}, {1, 4},
                        {2, 0}, {2, 1}, {2, 2}, {2, 3}, {2, 4},
                        {3, 0}, {3, 1}, {3, 2}, {3, 3}, {3, 4},
                        {4, 0}, {4, 1}, {4, 2}, {4, 3}, {4, 4}}).toArray(int[][]::new));
        assertFalse(engine.validateBingo(card, all, "SINGLE_LINE"));
        assertFalse(engine.validateBingo(card, all, "BLACKOUT"));
        assertFalse(engine.validateBingo(card, all, "X_SHAPE"));
        assertFalse(engine.validateBingo(card, all, "MYSTERY_PATTERN"));
    }

    // ===== persisted manual daubs =====

    @Test
    @DisplayName("parseMarkedNumbers: blank/null/invalid handled safely")
    void parseMarks() {
        GameCard card = new GameCard();
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(null));
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(card));
        card.setMarkedNumbers("  ");
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(card));
        card.setMarkedNumbers("7, 12 ,0,44");
        assertEquals(List.of(7, 12, 0, 44), GameEngineService.parseMarkedNumbers(card));
        card.setMarkedNumbers("7,oops");
        assertEquals(List.of(), GameEngineService.parseMarkedNumbers(card));
    }
}
