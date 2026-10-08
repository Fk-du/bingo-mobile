package com.bingo.app.master.service;

import com.bingo.app.master.entity.PlatformConfig;
import com.bingo.app.master.repository.PlatformConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final PlatformConfigRepository configRepository;

    @Value("${bingo.card-size:25}")
    private int defaultCardSize;

    @Value("${bingo.number-range:75}")
    private int defaultNumberRange;

    @Value("${bingo.auto-call-interval-ms:5000}")
    private int defaultAutoCallInterval;

    @Value("${app.game.default-entry-fee:10}")
    private int defaultEntryFee;

    @Value("${app.game.min-withdrawal:100}")
    private int defaultMinWithdrawal;

    @Value("${bingo.fees.owner-fee-percent:30}")
    private BigDecimal defaultOwnerFeePercent;

    @Value("${bingo.fees.min-prize-percent:50}")
    private BigDecimal defaultMinPrizePercent;

    @Value("${bingo.fees.max-prize-percent:90}")
    private BigDecimal defaultMaxPrizePercent;

    @Value("${app.telegram.registration-bot.username:}")
    private String registrationBotUsername;

    /** Minimal config safe to expose without authentication (used by the mobile app). */
    public Map<String, Object> publicInfo() {
        return Map.of(
                "registrationBotUsername", registrationBotUsername
        );
    }

    @PostConstruct
    public void seedDefaults() {
        try {
            for (var entry : defaults().entrySet()) {
                if (!configRepository.existsById(entry.getKey())) {
                    configRepository.save(new PlatformConfig(entry.getKey(), entry.getValue()));
                    log.info("Seeded default config: {}={}", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("Could not seed default config (table may not be ready yet): {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAll() {
        try {
            seedMissingDefaults();
            var entries = configRepository.findAll();
            Map<String, Object> result = entries.stream()
                    .collect(Collectors.toMap(
                            PlatformConfig::getKey,
                            c -> parseValue(c.getValue())
                    ));
            // Runtime config that the clients (esp. the mobile app) need but is
            // kept out of the DB: registration bot username for the NO_PASSWORD
            // "open the bot" screen.
            result.putIfAbsent("registrationBotUsername", registrationBotUsername);
            return result;
        } catch (Exception e) {
            log.warn("Failed to load config from database: {}", e.getMessage());
            return getDefaultMap();
        }
    }

    private void seedMissingDefaults() {
        var existing = configRepository.findAll();
        var existingKeys = existing.stream().map(PlatformConfig::getKey).collect(Collectors.toSet());

        for (var entry : defaults().entrySet()) {
            if (!existingKeys.contains(entry.getKey())) {
                configRepository.save(new PlatformConfig(entry.getKey(), entry.getValue()));
                log.info("Seeded default config: {}={}", entry.getKey(), entry.getValue());
            }
        }

        // Settings a later release retired would otherwise linger in the table and keep
        // showing up in the super-admin screen, since getAll() returns every stored row.
        for (String retired : RETIRED_KEYS) {
            if (existingKeys.contains(retired)) {
                configRepository.deleteById(retired);
                log.info("Removed retired config: {}", retired);
            }
        }
    }

    /**
     * Config keys that no longer drive anything. {@code maxWinners} went away when the
     * simultaneous-winner cap was dropped: the pot is now shared between however many
     * distinct players claimed, so there is no limit left to configure.
     */
    private static final List<String> RETIRED_KEYS = List.of("maxWinners", "maxPlayers");

    private Map<String, String> defaults() {
        return Map.of(
                "cardSize", String.valueOf(defaultCardSize),
                "numberRange", String.valueOf(defaultNumberRange),
                "autoCallInterval", String.valueOf(defaultAutoCallInterval),
                "entryFee", String.valueOf(defaultEntryFee),
                "minWithdrawal", String.valueOf(defaultMinWithdrawal),
                "ownerFeePercent", String.valueOf(defaultOwnerFeePercent),
                "minPrizePercent", String.valueOf(defaultMinPrizePercent),
                "maxPrizePercent", String.valueOf(defaultMaxPrizePercent)
        );
    }

    @Transactional
    public void updateAll(Map<String, Object> config) {
        config.forEach((key, value) -> {
            var entity = configRepository.findById(key)
                    .orElse(new PlatformConfig(key, null));
            entity.setValue(String.valueOf(value));
            configRepository.save(entity);
        });
    }

    public BigDecimal getMinWithdrawal() {
        try {
            Object value = getAll().get("minWithdrawal");
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
        } catch (Exception e) {
            log.warn("Failed to read minWithdrawal config, using default: {}", e.getMessage());
        }
        return BigDecimal.valueOf(defaultMinWithdrawal);
    }

    private Map<String, Object> getDefaultMap() {
        return Map.of(
                "cardSize", defaultCardSize,
                "numberRange", defaultNumberRange,
                "autoCallInterval", defaultAutoCallInterval,
                "entryFee", defaultEntryFee,
                "minWithdrawal", defaultMinWithdrawal,
                "ownerFeePercent", defaultOwnerFeePercent,
                "minPrizePercent", defaultMinPrizePercent,
                "maxPrizePercent", defaultMaxPrizePercent
        );
    }

    /**
     * Share (in %) the super admin takes out of each admin's per-game commission
     * (the pot minus the prize). The agent keeps the rest; the owner gets a
     * PLATFORM_FEE ledger row for every settled game. Editable by the super admin
     * from the app's config screen, so the default is only a starting point.
     */
    public BigDecimal getOwnerFeePercent() {
        return percent("ownerFeePercent", defaultOwnerFeePercent);
    }

    /**
     * Floor for the prize an admin may set, as a % of the pot collected so far.
     * The prize is chosen by the admin, so this is what stops a game paying out a
     * token amount and keeping the rest.
     */
    public BigDecimal getMinPrizePercent() {
        return percent("minPrizePercent", defaultMinPrizePercent);
    }

    /** Ceiling for the prize an admin may set, as a % of the pot collected so far. */
    public BigDecimal getMaxPrizePercent() {
        return percent("maxPrizePercent", defaultMaxPrizePercent);
    }

    private BigDecimal percent(String key, BigDecimal fallback) {
        try {
            Object value = getAll().get(key);
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
        } catch (Exception e) {
            log.warn("Failed to read {} config, using default: {}", key, e.getMessage());
        }
        return fallback;
    }

    private Object parseValue(String raw) {
        try {
            if (raw.contains(".")) {
                return Double.parseDouble(raw);
            }
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
    }
}
