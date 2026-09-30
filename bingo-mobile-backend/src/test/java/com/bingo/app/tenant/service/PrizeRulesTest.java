package com.bingo.app.tenant.service;

import com.bingo.app.master.service.ConfigService;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.exception.GameProgressException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * The prize is chosen by the admin, so the guardrails around it are the only
 * thing standing between the platform and a game that pays out a token amount
 * and keeps the rest. These cover both edges of the allowed range.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrizeRulesTest {

    @Mock ConfigService configService;

    PrizeRules rules;

    @BeforeEach
    void setUp() {
        when(configService.getMinPrizePercent()).thenReturn(new BigDecimal("50"));
        when(configService.getMaxPrizePercent()).thenReturn(new BigDecimal("90"));
        rules = new PrizeRules(configService);
    }

    private Game game(String pool) {
        return Game.builder()
                .id(1L)
                .adminUserId(2L)
                .prizePool(new BigDecimal(pool))
                .build();
    }

    @Test
    @DisplayName("admin commission is what the pot has left after the prize")
    void commissionIsTheRemainder() {
        Game g = game("100.00");
        g.setPrizeAmount(new BigDecimal("70.00"));

        assertEquals(0, new BigDecimal("30.00").compareTo(rules.commissionFor(g)));
        assertEquals(0, new BigDecimal("70.00").compareTo(rules.prizeFor(g)));
    }

    @Test
    @DisplayName("a game with no prize set pays nothing yet, so the whole pot is still the admin's")
    void noPrizeYetIsZero() {
        assertEquals(0, new BigDecimal("100.00").compareTo(rules.commissionFor(game("100.00"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(rules.prizeFor(game("100.00"))));
    }

    @Test
    @DisplayName("bounds come from the platform config: 50% floor, 90% ceiling")
    void boundsFollowConfig() {
        assertEquals(0, new BigDecimal("50.00").compareTo(rules.minPrizeFor(new BigDecimal("100.00"))));
        assertEquals(0, new BigDecimal("90.00").compareTo(rules.maxPrizeFor(new BigDecimal("100.00"))));
        // the range scales with the pot
        assertEquals(0, new BigDecimal("25.00").compareTo(rules.minPrizeFor(new BigDecimal("50.00"))));
    }

    @Test
    @DisplayName("a prize inside the range is accepted and stored")
    void acceptsPrizeInsideRange() {
        Game g = game("100.00");

        BigDecimal stored = rules.applyPrize(g, new BigDecimal("75.00"));

        assertEquals(0, new BigDecimal("75.00").compareTo(stored));
        assertEquals(0, new BigDecimal("75.00").compareTo(g.getPrizeAmount()));
    }

    @Test
    @DisplayName("a prize below the floor is rejected: the admin cannot keep the whole pot")
    void rejectsPrizeBelowFloor() {
        Game g = game("100.00");

        GameProgressException e = assertThrows(GameProgressException.class,
                () -> rules.applyPrize(g, new BigDecimal("10.00")));

        assertTrue(e.getMessage().contains("minimum"), "the failure names the floor");
        assertNull(g.getPrizeAmount(), "a rejected prize is not stored");
    }

    @Test
    @DisplayName("a prize above the ceiling is rejected: winners cannot be overpaid from the pot")
    void rejectsPrizeAboveCeiling() {
        Game g = game("100.00");

        GameProgressException e = assertThrows(GameProgressException.class,
                () -> rules.applyPrize(g, new BigDecimal("99.00")));

        assertTrue(e.getMessage().contains("maximum"), "the failure names the ceiling");
        assertNull(g.getPrizeAmount());
    }

    @Test
    @DisplayName("the exact floor and ceiling are both allowed")
    void boundsAreInclusive() {
        assertEquals(0, new BigDecimal("50.00")
                .compareTo(rules.applyPrize(game("100.00"), new BigDecimal("50.00"))));
        assertEquals(0, new BigDecimal("90.00")
                .compareTo(rules.applyPrize(game("100.00"), new BigDecimal("90.00"))));
    }

    @Test
    @DisplayName("a game cannot start until the admin has set a prize")
    void startRequiresAPrize() {
        GameProgressException e = assertThrows(GameProgressException.class,
                () -> rules.requirePrizeSet(game("100.00")));

        assertTrue(e.getMessage().contains("prize"), "the admin is told what is missing");
    }

    @Test
    @DisplayName("a prize that fell out of range as the pot grew blocks the start")
    void startRevalidatesAgainstTheGrownPot() {
        // Prize set when the pot was 100, so 70 was a fair 70%. By the time the
        // admin starts, ten more players have joined and the same 70 is only 40%.
        Game g = game("175.00");
        g.setPrizeAmount(new BigDecimal("70.00"));

        assertThrows(GameProgressException.class, () -> rules.requirePrizeSet(g));
    }

    @Test
    @DisplayName("a still-valid prize passes the start re-check untouched")
    void startAcceptsStillValidPrize() {
        Game g = game("120.00");
        g.setPrizeAmount(new BigDecimal("80.00"));

        rules.requirePrizeSet(g);

        assertEquals(0, new BigDecimal("80.00").compareTo(g.getPrizeAmount()));
    }

    @Test
    @DisplayName("the suggestion follows the admin's preferred rake and stays settable")
    void suggestionHonoursPreferredRake() {
        // 100 pot, admin prefers a 20% rake -> 80 prize, which is inside 50-90.
        assertEquals(0, new BigDecimal("80.00")
                .compareTo(rules.suggestedPrize(new BigDecimal("100.00"), new BigDecimal("20"))));
    }

    @Test
    @DisplayName("a preferred rake outside the guardrails is clamped into range")
    void suggestionIsClamped() {
        // A 5% rake would pay out 95, over the ceiling, so it is pulled back to 90.
        assertEquals(0, new BigDecimal("90.00")
                .compareTo(rules.suggestedPrize(new BigDecimal("100.00"), new BigDecimal("5"))));
        // A 80% rake would pay out 20, under the floor, so it is lifted to 50.
        assertEquals(0, new BigDecimal("50.00")
                .compareTo(rules.suggestedPrize(new BigDecimal("100.00"), new BigDecimal("80"))));
    }

    @Test
    @DisplayName("commission is clamped at zero, so a mis-set prize can never post a negative cut")
    void commissionNeverGoesNegative() {
        // The 90% ceiling means this cannot be reached through applyPrize; the clamp
        // is a backstop against a prize written straight to the column.
        Game g = game("100.00");
        g.setPrizeAmount(new BigDecimal("100.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(rules.commissionFor(g)));
    }
}
