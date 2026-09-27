package com.bingo.app.bot.i18n;

import com.bingo.app.master.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every word the bots say, in English and Amharic.
 *
 * <p>Text lives in {@code bot-text.json} rather than in Java strings so both
 * languages sit side by side and a translator can edit them without touching
 * code. Messages are sent in both languages — English first, then Amharic —
 * because a user can change their Telegram language at any time and the bot
 * should never be the reason they cannot read it.</p>
 */
@Service
@Slf4j
public class BotText {

    /** Telegram reports Amharic as {@code am} or {@code am-ET}. */
    private static final String AMHARIC = "am";
    private static final String ENGLISH = "en";
    private static final String DIVIDER = "───────────";
    private static final String RESOURCE = "bot-text.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile JsonNode tree;

    private synchronized JsonNode tree() {
        if (tree == null) {
            tree = load();
        }
        return tree;
    }

    private JsonNode load() {
        try (InputStream in = BotText.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.error("{} is not on the classpath — the bots will answer with key names", RESOURCE);
                return null;
            }
            JsonNode loaded = objectMapper.readTree(in);
            log.info("Loaded {} bot messages in {} languages", loaded.size(),
                    loaded.fields().next().getValue().size());
            return loaded;
        } catch (Exception e) {
            log.error("Failed to load {}: {}", RESOURCE, e.getMessage());
            return null;
        }
    }

    /**
     * The message in both languages, English first.
     *
     * @param args optional {@code {name}} placeholders, given in pairs
     *             ({@code "phone", "+251..."}).
     */
    public String t(String key, Object... args) {
        return bilingual(key, args);
    }

    /** English and Amharic blocks joined by a neutral divider. */
    public String bilingual(String key, Object... args) {
        String en = render(key, ENGLISH, args);
        String am = render(key, AMHARIC, args);
        if (en == null || am == null) {
            // A half-translated key must never leave a blank message behind.
            return en != null ? en : am != null ? am : "[" + key + "]";
        }
        if (en.equals(am)) {
            return en;
        }
        return en + "\n\n" + DIVIDER + "\n\n" + am;
    }

    /** One language only, for the rare place that cannot show two. */
    public String one(String key, String language, Object... args) {
        String value = render(key, language, args);
        return value != null ? value : "[" + key + "]";
    }

    /**
     * A short label for a button, which has far less room than a message:
     * {@code "📱 Share Phone / ስልክ አጋራ / ቁጥር"}.
     */
    public String label(String key) {
        String en = render(key, ENGLISH);
        String am = render(key, AMHARIC);
        if (en == null) {
            return am != null ? am : key;
        }
        return am == null || en.equals(am) ? en : en + " / " + am;
    }

    private String render(String key, String language, Object... args) {
        JsonNode messages = tree();
        if (messages == null) {
            return null;
        }
        JsonNode entry = messages.get(key);
        if (entry == null) {
            log.warn("Missing bot message key: {}", key);
            return null;
        }
        String text = entry.path(language).asText(null);
        return text == null ? null : fill(text, args);
    }

    /** Replaces {@code {name}} placeholders; unknown placeholders are left visible. */
    private String fill(String text, Object... args) {
        if (args == null || args.length == 0) {
            return text;
        }
        String result = text;
        for (int i = 0; i + 1 < args.length; i += 2) {
            result = result.replace("{" + args[i] + "}", String.valueOf(args[i + 1]));
        }
        return result;
    }

    /**
     * The language Telegram reports for this chat, or the one saved on the
     * account, or English.
     */
    public String languageOf(Update update) {
        if (update == null) {
            return ENGLISH;
        }
        if (update.getMessage() != null && update.getMessage().getFrom() != null
                && AMHARIC.equals(languageOfCode(update.getMessage().getFrom().getLanguageCode()))) {
            return AMHARIC;
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null
                && AMHARIC.equals(languageOfCode(update.getCallbackQuery().getFrom().getLanguageCode()))) {
            return AMHARIC;
        }
        return ENGLISH;
    }

    public String languageOf(User user) {
        return user == null ? ENGLISH : languageOfCode(user.getPreferredLanguage());
    }

    /**
     * Map any language tag to a catalogue language. Telegram sends
     * {@code am}, {@code am-ET} or {@code AM_ET} for Amharic; anything unknown
     * falls back to English rather than showing an empty message.
     */
    public String languageOfCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return ENGLISH;
        }
        String normalized = languageCode.toLowerCase().replace('_', '-');
        return normalized.startsWith(AMHARIC) ? AMHARIC : ENGLISH;
    }
}
