package com.bingo.app.master.dto.request;

import com.bingo.app.master.dto.DepositAccount;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Optional fields to change on the caller's own profile; absent fields stay untouched. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProfileRequest(
        String preferredLanguage,
        String businessName,
        List<DepositAccount> depositAccounts
) {
}