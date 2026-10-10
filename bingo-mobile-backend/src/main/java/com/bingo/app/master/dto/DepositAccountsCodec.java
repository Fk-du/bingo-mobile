package com.bingo.app.master.dto;

import com.bingo.app.master.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts {@link User#getDepositAccountInfo()} (free text) to/from a structured list. */
@Component
public class DepositAccountsCodec {

    private final ObjectMapper objectMapper;

    public DepositAccountsCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A JSON array is deserialized into the account list; any other saved text is the
     * legacy free-form answer and becomes one account's number so players still see it.
     */
    public List<DepositAccount> read(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) {
            return List.of(new DepositAccount(null, trimmed, null));
        }
        try {
            List<DepositAccount> parsed = objectMapper.readValue(
                    trimmed, new TypeReference<List<DepositAccount>>() {});
            return parsed == null ? List.of() : parsed.stream().map(DepositAccount::sanitize).toList();
        } catch (Exception e) {
            return List.of(new DepositAccount(null, trimmed, null));
        }
    }

    public String write(List<DepositAccount> accounts) {
        try {
            return objectMapper.writeValueAsString(accounts);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not save deposit accounts", e);
        }
    }
}