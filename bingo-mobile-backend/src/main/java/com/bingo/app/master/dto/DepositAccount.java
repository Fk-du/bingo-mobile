package com.bingo.app.master.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One bank/mobile-money account an admin accepts deposits into. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DepositAccount(String bank, String accountNumber, String ownerName) {

    /**
     * Trims every field to a clean value; {@code bank} and {@code ownerName} with
     * no real content end up {@code null} while {@code accountNumber} is never null.
     */
    public static DepositAccount sanitize(DepositAccount account) {
        if (account == null) {
            return null;
        }
        return new DepositAccount(
                blankToNull(account.bank()),
                account.accountNumber() == null ? "" : account.accountNumber().trim(),
                blankToNull(account.ownerName()));
    }

    public boolean hasNumber() {
        return accountNumber != null && !accountNumber.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}