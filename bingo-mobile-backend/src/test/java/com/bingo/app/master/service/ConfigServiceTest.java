package com.bingo.app.master.service;

import com.bingo.app.master.entity.PlatformConfig;
import com.bingo.app.master.repository.PlatformConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The fee rates that shape every settlement. The defaults matter: an operator
 * who never opens the config screen still gets a 30% owner fee and a 50-90%
 * band for the admin's prize.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigServiceTest {

    @Mock PlatformConfigRepository configRepository;

    @InjectMocks ConfigService configService;

    /** {@code getAll()} reads the key/value table, so rows have to be returned, not just "exists". */
    private void seed(String... keysAndValues) {
        ReflectionTestUtils.setField(configService, "defaultOwnerFeePercent", new BigDecimal("30"));
        ReflectionTestUtils.setField(configService, "defaultMinPrizePercent", new BigDecimal("50"));
        ReflectionTestUtils.setField(configService, "defaultMaxPrizePercent", new BigDecimal("90"));
        ReflectionTestUtils.setField(configService, "defaultCardSize", 25);
        ReflectionTestUtils.setField(configService, "defaultNumberRange", 75);
        ReflectionTestUtils.setField(configService, "defaultAutoCallInterval", 5);
        ReflectionTestUtils.setField(configService, "defaultEntryFee", 10);
        ReflectionTestUtils.setField(configService, "defaultMinWithdrawal", 10);

        when(configRepository.existsById(anyString())).thenReturn(false);
        var rows = new java.util.ArrayList<PlatformConfig>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            rows.add(new PlatformConfig(keysAndValues[i], keysAndValues[i + 1]));
        }
        // seedMissingDefaults() runs inside getAll() and saves anything missing, so
        // the second read has to include whatever it just wrote.
        var all = new java.util.ArrayList<>(rows);
        when(configRepository.save(any())).thenAnswer(inv -> {
            all.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(configRepository.findAll()).thenAnswer(inv -> List.copyOf(all));
    }

    @Test
    @DisplayName("with nothing configured the owner takes 30% of each admin's commission")
    void ownerFeeDefaultsToThirtyPercent() {
        seed();

        assertEquals(0, new BigDecimal("30").compareTo(configService.getOwnerFeePercent()));
    }

    @Test
    @DisplayName("a super admin can change the owner fee")
    void ownerFeeIsConfigurable() {
        seed("ownerFeePercent", "45");

        assertEquals(0, new BigDecimal("45").compareTo(configService.getOwnerFeePercent()));
    }

    @Test
    @DisplayName("the prize band defaults to 50-90% of the pot")
    void prizeBandDefaults() {
        seed();

        assertEquals(0, new BigDecimal("50").compareTo(configService.getMinPrizePercent()));
        assertEquals(0, new BigDecimal("90").compareTo(configService.getMaxPrizePercent()));
    }

    @Test
    @DisplayName("the legacy ownerShareRate key is ignored so the new default applies")
    void legacyOwnerShareRateIsNotUsed() {
        seed("ownerShareRate", "20");

        assertEquals(0, new BigDecimal("30").compareTo(configService.getOwnerFeePercent()));
    }

    @Test
    @DisplayName("the retired maxWinners row is dropped so it stops showing in the config screen")
    void retiredMaxWinnersIsRemoved() {
        seed("maxWinners", "3");

        configService.getAll();

        org.mockito.Mockito.verify(configRepository).deleteById("maxWinners");
    }

    @Test
    @DisplayName("config seeded for a fresh install carries the new keys")
    void defaultsArePublished() {
        seed();
        var saved = new java.util.ArrayList<PlatformConfig>();
        when(configRepository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        configService.seedDefaults();

        var keys = saved.stream().map(PlatformConfig::getKey).toList();
        org.junit.jupiter.api.Assertions.assertTrue(keys.contains("ownerFeePercent"));
        org.junit.jupiter.api.Assertions.assertTrue(keys.contains("minPrizePercent"));
        org.junit.jupiter.api.Assertions.assertTrue(keys.contains("maxPrizePercent"));
        org.junit.jupiter.api.Assertions.assertFalse(keys.contains("maxWinners"),
                "winners are uncapped, so there is no maxWinners setting to seed");
    }
}
