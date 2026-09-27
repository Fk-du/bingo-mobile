package com.bingo.app.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password encoder lives here rather than in {@link SecurityConfig} so it
 * has no dependencies of its own.
 *
 * <p>Inside the security filter configuration it sat behind the whole filter
 * chain — TokenAuthFilter, the Telegram auth service, the bot and its
 * handlers — so anything that needed a {@code PasswordEncoder} while the bot
 * was being constructed (the registration flow does) completed the cycle
 * SecurityConfig -> TokenAuthFilter -> TelegramAuthService -> bot -> handler
 * -> PasswordEncoder -> SecurityConfig, and startup failed.</p>
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
