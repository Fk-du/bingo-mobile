package com.bingo.app.tenant.service;

import com.bingo.app.tenant.entity.AutomationConfig;
import com.bingo.app.tenant.repository.AutomationConfigRepository;
import com.bingo.app.tenant.repository.BingoClaimRepository;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * How a stored automation config's winning pattern is read back. Only the canonical
 * 29 patterns are saveable, so a stored code — canonical or not — is passed through
 * as-is for the admin to see; automatic games ignore it and randomise from the
 * canonical list anyway.
 */
class AutomationConfigPatternTest {

    private final AutomationConfigRepository automationConfigRepository = mock(AutomationConfigRepository.class);
    private final GameAutomationService service = new GameAutomationService(
            automationConfigRepository,
            mock(GameRepository.class),
            mock(GameCardRepository.class),
            mock(BingoClaimRepository.class),
            mock(GameService.class),
            mock(GameEngineService.class),
            mock(com.bingo.app.master.repository.TenantRegistryRepository.class),
            mock(TransactionTemplate.class)
    );

    private void storedWith(String pattern) {
        when(automationConfigRepository.findByAdminUserId(1L)).thenReturn(Optional.of(
                AutomationConfig.builder()
                        .adminUserId(1L)
                        .winningPattern(pattern)
                        .entryFee(new java.math.BigDecimal("10.00"))
                        .build()));
    }

    @Test
    @DisplayName("a non-canonical stored pattern is returned unchanged, not guessed at")
    void nonCanonicalStoredPatternIsUnchanged() {
        storedWith("SINGLE_LINE");

        assertEquals("SINGLE_LINE", service.getConfig(1L).winningPattern());
    }

    @Test
    @DisplayName("a canonical stored pattern is returned unchanged")
    void canonicalStoredPatternIsUnchanged() {
        storedWith("PLUS_SHAPE");

        assertEquals("PLUS_SHAPE", service.getConfig(1L).winningPattern());
    }

    @Test
    @DisplayName("a missing stored pattern reads back as FULL_HOUSE")
    void blankStoredPatternReadsAsFullHouse() {
        storedWith(null);

        assertEquals("FULL_HOUSE", service.getConfig(1L).winningPattern());
    }
}
