package com.bingo.app.bot.onboarding;

/**
 * Button labels and callback actions for the registration-only bot.
 * Kept separate from {@link com.bingo.app.bot.BotConstants} so the game bot
 * stays untouched.
 */
public final class OnboardingBotConstants {

    private OnboardingBotConstants() {
    }

    public static final String BTN_SHARE_PHONE = "\uD83D\uDCF1 Share Phone Number";
    public static final String BTN_CREATE_PASSWORD = "\uD83D\uDD11 Create Password (for mobile app)";

    public static final class Actions {
        public static final String CREATE_PASSWORD = "ONBOARD_CREATE_PASSWORD";
        public static final String SKIP_PASSWORD = "ONBOARD_SKIP_PASSWORD";
    }
}