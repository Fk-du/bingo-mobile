package com.bingo.app.bot.onboarding;

/**
 * Callback actions for the registration flow. Button labels are not here: they
 * live in the bilingual catalogue ({@code bot-text.json}) so every button says
 * the same thing in English and Amharic.
 */
public final class OnboardingBotConstants {

    private OnboardingBotConstants() {
    }

    public static final class Actions {
        public static final String CREATE_PASSWORD = "ONBOARD_CREATE_PASSWORD";
        public static final String SKIP_PASSWORD = "ONBOARD_SKIP_PASSWORD";
    }
}
