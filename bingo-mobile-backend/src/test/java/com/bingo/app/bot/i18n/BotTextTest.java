package com.bingo.app.bot.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bots have no strings of their own any more: every word comes from
 * {@code bot-text.json} in English and Amharic. These tests guard the two ways
 * that can silently break — a missing or half-written key, and a message that
 * ends up in one language only.
 */
class BotTextTest {

    private final BotText text = new BotText();

    @Test
    @DisplayName("every message is answered in both English and Amharic")
    void everyMessageCarriesBothLanguages() {
        for (String key : keys()) {
            String message = text.t(key);
            assertFalse(message.startsWith("["), key + " has no entry in the catalogue");
            assertTrue(message.contains(text.one(key, "en")), key + " is missing its English half");
            assertTrue(message.contains(text.one(key, "am")), key + " is missing its Amharic half");
            if (!key.startsWith("btn_")) {
                // Button labels are one bilingual string on purpose — a press comes
                // back as plain text and has to match exactly.
                assertNotEquals(text.one(key, "en"), text.one(key, "am"),
                        key + " is the same in both languages");
            }
        }
    }

    @Test
    @DisplayName("the Amharic side is actually in Amharic script")
    void amharicIsNotEmptyOrLatin() {
        for (String key : keys()) {
            String am = text.one(key, "am");
            assertFalse(am.isBlank(), key + " has no Amharic text");
            boolean hasEthiopic = am.codePoints().anyMatch(c -> c >= 0x1200 && c <= 0x137F);
            boolean isButtonLabel = key.startsWith("btn_");
            if (!isButtonLabel) {
                assertTrue(hasEthiopic, key + " has no Amharic script: " + am);
            }
        }
    }

    @Test
    @DisplayName("no placeholder is left unfilled")
    void placeholdersAreFilled() {
        String tooShort = text.t("password_too_short", "min", 6);
        assertTrue(tooShort.contains("6"), tooShort);
        assertFalse(tooShort.contains("{min}"), tooShort);

        String registered = text.t("registered_with_phone", "phone", "+251911000111");
        assertTrue(registered.contains("+251911000111"), registered);
        assertFalse(registered.contains("{phone}"), registered);
    }

    @Test
    @DisplayName("a missing key degrades to its name instead of sending nothing")
    void missingKeyIsVisible() {
        assertTrue(text.t("no_such_key").contains("no_such_key"));
        assertEquals("no_such_key", text.label("no_such_key"));
    }

    @Test
    @DisplayName("button labels are short enough for a keyboard and carry both languages")
    void buttonLabelsAreBilingualAndCompact() {
        String sharePhone = text.label("btn_share_phone");
        assertTrue(sharePhone.contains("Share Phone"), sharePhone);
        assertTrue(sharePhone.contains("ስልክ"), sharePhone);
        assertTrue(sharePhone.length() <= 64, "reply keyboard labels get truncated: " + sharePhone);

        // A button press comes back as plain text, so its label has to be one
        // stable string rather than "en or am, whichever was shown".
        assertEquals(sharePhone, text.one("btn_share_phone", "en"));
    }

    @Test
    @DisplayName("Telegram's Amharic language codes are recognised")
    void amharicLanguageCodesAreRecognised() {
        for (String tag : new String[] { "am", "am-ET", "AM_ET", "am-et" }) {
            assertEquals("am", text.languageOfCode(tag), tag);
        }
        for (String tag : new String[] { "en", "en-US", null, "", "fr" }) {
            assertEquals("en", text.languageOfCode(tag), String.valueOf(tag));
        }
        assertEquals("en", text.languageOf((Update) null));
    }

    private java.util.Set<String> keys() {
        // Reach the catalogue the same way the bots do, without a Spring context.
        java.util.Set<String> keys = new java.util.TreeSet<>();
        for (String key : new String[] {
                "welcome_back", "registered_admin", "registered_player", "super_admin_welcome",
                "invalid_invite", "registration_failed", "unknown_user_need_invite",
                "unknown_user_need_invite_short", "phone_request_full", "phone_request_short",
                "phone_read_failed", "phone_saved", "registered_with_phone", "username_tip",
                "already_has_password", "password_prompt", "password_too_short", "password_confirm",
                "password_mismatch", "password_type_new", "password_saved", "password_save_failed",
                "password_skipped", "account_not_found", "nothing_to_type", "error_generic",
                "btn_share_phone", "btn_create_password", "btn_skip_password" }) {
            keys.add(key);
        }
        return keys;
    }

}
