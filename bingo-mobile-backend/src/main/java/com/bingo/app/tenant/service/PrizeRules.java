package com.bingo.app.tenant.service;

import com.bingo.app.master.service.ConfigService;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.exception.GameProgressException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The money rules for a game, in one place.
 *
 * <p>An admin sets an absolute {@code prizeAmount} on a game while registration
 * is still open, after seeing how many players signed up. The pot keeps growing
 * as cards are sold, so the prize is re-checked against the pot when the game
 * starts. Whatever the pot has left over after the prize is the admin's
 * commission, and the owner takes {@code ownerFeePercent} of that.
 */
@Service
@RequiredArgsConstructor
public class PrizeRules {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final ConfigService configService;

    /**
     * The prize this game pays out. Zero for a game that never got one (a game
     * that ended in REGISTRATION_OPEN is refunded in full, so it never settles).
     */
    public BigDecimal prizeFor(Game game) {
        return money(game.getPrizeAmount() == null ? ZERO : game.getPrizeAmount());
    }

    /**
     * The admin's cut: the pot minus the prize. Clamped at zero so a game that
     * paid out its whole pot can never post a negative commission.
     */
    public BigDecimal commissionFor(Game game) {
        BigDecimal pool = money(game.getPrizePool());
        return pool.subtract(prizeFor(game)).max(ZERO);
    }

    /** Smallest prize the admin may set for a pot of this size. */
    public BigDecimal minPrizeFor(BigDecimal pot) {
        return percentOf(pot, configService.getMinPrizePercent());
    }

    /** Largest prize the admin may set for a pot of this size. */
    public BigDecimal maxPrizeFor(BigDecimal pot) {
        return percentOf(pot, configService.getMaxPrizePercent());
    }

    /**
     * Check an admin's chosen prize against the pot collected so far, and store
     * it. Called both when the prize is set and again when the game starts,
     * because more players can register in between and shrink the rake.
     *
     * @return the stored prize
     */
    public BigDecimal applyPrize(Game game, BigDecimal prize) {
        BigDecimal pool = money(game.getPrizePool());
        BigDecimal min = minPrizeFor(pool);
        BigDecimal max = maxPrizeFor(pool);

        if (prize == null) {
            throw new GameProgressException("No prize amount given",
                    "Enter the prize you want to pay the winners.");
        }
        if (prize.signum() <= 0) {
            throw new GameProgressException("Prize must be greater than 0",
                    "The prize must be more than 0.");
        }
        if (prize.compareTo(min) < 0) {
            throw new GameProgressException("Prize " + prize + " is below the minimum " + min,
                    "The prize cannot be less than " + min.toPlainString()
                            + " (the minimum for the " + pool.toPlainString() + " collected so far).");
        }
        if (prize.compareTo(max) > 0) {
            throw new GameProgressException("Prize " + prize + " is above the maximum " + max,
                    "The prize cannot be more than " + max.toPlainString()
                            + " (the maximum for the " + pool.toPlainString() + " collected so far).");
        }

        game.setPrizeAmount(money(prize));
        return game.getPrizeAmount();
    }

    /**
     * Called by the start path. A game with no prize has no agreed payout, so it
     * must not go live — the admin sets the prize first.
     */
    public void requirePrizeSet(Game game) {
        if (game.getPrizeAmount() == null) {
            throw new GameProgressException("Game has no prize set",
                    "Set the prize for the winners before starting this game.");
        }
        // Re-validate: the pot grew after the prize was set, which may have moved
        // the percentage the prize represents outside the allowed range.
        applyPrize(game, game.getPrizeAmount());
    }

    /**
     * A prize to offer the admin for this pot, from the rake they prefer in their
     * automation settings. Clamped into the allowed range so the suggestion is
     * always settable.
     */
    public BigDecimal suggestedPrize(BigDecimal pot, BigDecimal preferredRakePercent) {
        BigDecimal pool = poolOf(pot);
        BigDecimal rake = percentOf(pool, preferredRakePercent);
        return money(pool.subtract(rake).max(minPrizeFor(pool)).min(maxPrizeFor(pool)));
    }

    private BigDecimal poolOf(BigDecimal pot) {
        return pot == null ? ZERO : money(pot);
    }

    private BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
