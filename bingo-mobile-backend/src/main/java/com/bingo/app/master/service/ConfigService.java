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
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final PlatformConfigRepository configRepository;

    @Value("${bingo.max-winners:3}")
    private int defaultMaxWinners;

    @Value("${bingo.card-size:25}")
    private int defaultCardSize;

    @Value("${bingo.number-range:75}")
    private int defaultNumberRange;

    @Value("${bingo.auto-call-interval-ms:5000}")
    private int defaultAutoCallInterval;

    @Value("${app.game.default-entry-fee:10}")
    private int defaultEntryFee;

    @Value("${app.game.default-max-players:50}")
    private int defaultMaxPlayers;

    @Value("${app.game.min-withdrawal:10}")
    private int defaultMinWithdrawal;

    @Value("${bingo.fees.owner-share-rate-percent:20}")
    private BigDecimal defaultOwnerShareRate;

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
    }

    private Map<String, String> defaults() {
        return Map.of(
                "maxWinners", String.valueOf(defaultMaxWinners),
                "cardSize", String.valueOf(defaultCardSize),
                "numberRange", String.valueOf(defaultNumberRange),
                "autoCallInterval", String.valueOf(defaultAutoCallInterval),
                "entryFee", String.valueOf(defaultEntryFee),
                "maxPlayers", String.valueOf(defaultMaxPlayers),
                "minWithdrawal", String.valueOf(defaultMinWithdrawal),
                "ownerShareRate", String.valueOf(defaultOwnerShareRate)
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
                "maxWinners", defaultMaxWinners,
                "cardSize", defaultCardSize,
                "numberRange", defaultNumberRange,
                "autoCallInterval", defaultAutoCallInterval,
                "entryFee", defaultEntryFee,
                "maxPlayers", defaultMaxPlayers,
                "minWithdrawal", defaultMinWithdrawal,
                "ownerShareRate", defaultOwnerShareRate
        );
    }

    /**
     * Share (in %) the super admin takes from each admin's per-game commission.
     * The agent keeps (100 - ownerShareRate)% of their own commission; the owner
     * gets credited a PLATFORM_FEE for every settled game.
     */
    public BigDecimal getOwnerShareRate() {
        try {
            Object value = getAll().get("ownerShareRate");
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
        } catch (Exception e) {
            log.warn("Failed to read ownerShareRate config, using default: {}", e.getMessage());
        }
        return defaultOwnerShareRate;
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
