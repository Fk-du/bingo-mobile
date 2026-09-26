package com.bingo.app.infrastructure.websocket;

import com.bingo.app.infrastructure.security.JwtTokenService;
import com.bingo.app.infrastructure.security.TelegramAuthService;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TelegramAuthService telegramAuthService;
    private final JwtTokenService jwtTokenService;
    private final UserService userService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor(telegramAuthService, jwtTokenService, userService))
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor(telegramAuthService, jwtTokenService, userService));
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new StompConnectAuthInterceptor(telegramAuthService, jwtTokenService, userService));
    }

    /**
     * Accepts either a Bearer JWT (mobile) or Telegram initData (web).
     */
    private static User authenticateAny(String token, TelegramAuthService telegramAuthService,
                                        JwtTokenService jwtTokenService, UserService userService) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            Long userId = jwtTokenService.parseSubject(token.substring(7));
            return userId == null ? null : userService.findByIdEntity(userId);
        }
        try {
            return telegramAuthService.authenticate(token);
        } catch (Exception e) {
            log.debug("Telegram WS auth failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * HTTP handshake interceptor — authenticates the initial WebSocket upgrade request.
     * Accepts a Bearer JWT (mobile) or Telegram initData (web) in the "token" query parameter.
     */
    @RequiredArgsConstructor
    private static class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {
        private final TelegramAuthService telegramAuthService;
        private final JwtTokenService jwtTokenService;
        private final UserService userService;

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            try {
                String token = extractTokenFromUri(request.getURI());
                if (token != null) {
                    User user = authenticateAny(token, telegramAuthService, jwtTokenService, userService);
                    if (user != null) {
                        UserPrincipal principal = new UserPrincipal(user);
                        attributes.put("userPrincipal", principal);
                        attributes.put("userPrincipal_auth",
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                        log.debug("WebSocket handshake authenticated: id={}", user.getId());
                        return true;
                    }
                }
                log.debug("WebSocket handshake: no valid token — allowing connection (auth required on STOMP CONNECT)");
                return true;
            } catch (Exception e) {
                log.error("WebSocket handshake auth error: {}", e.getMessage());
                return true;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private String extractTokenFromUri(URI uri) {
            String query = uri.getQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return kv[1];
                }
            }
            return null;
        }
    }

    /**
     * STOMP CONNECT interceptor — authenticates from the Authorization header.
     * Accepts {@code Bearer <jwt>} (mobile) or {@code tma <initData>} / native
     * "token" header (web). This is the primary auth path for STOMP connections.
     */
    @RequiredArgsConstructor
    private static class StompConnectAuthInterceptor implements ChannelInterceptor {
        private final TelegramAuthService telegramAuthService;
        private final JwtTokenService jwtTokenService;
        private final UserService userService;

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                String token = extractToken(accessor);

                if (token != null) {
                    User user = authenticateAny(token, telegramAuthService, jwtTokenService, userService);
                    if (user != null) {
                        UserPrincipal principal = new UserPrincipal(user);
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        accessor.setUser(auth);
                        log.debug("WebSocket STOMP CONNECT authenticated: id={}", user.getId());
                        return message;
                    }
                }

                log.warn("WebSocket STOMP CONNECT rejected: invalid or missing auth token");
                accessor.setLeaveMutable(true);
                return message;
            }

            return message;
        }

        private String extractToken(StompHeaderAccessor accessor) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
                if (authHeader.startsWith("tma ")) {
                    return authHeader.substring(4);
                }
            }

            List<String> tokenHeaders = accessor.getNativeHeader("token");
            if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
                return tokenHeaders.get(0);
            }

            return null;
        }
    }
}