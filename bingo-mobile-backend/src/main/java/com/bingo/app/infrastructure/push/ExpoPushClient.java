package com.bingo.app.infrastructure.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends push messages through the Expo push service ({@code exp.host}) so a
 * device shows a system notification even while the app is closed. Messages
 * are batched (the API takes arrays up to 100) and tokens Expo reports as
 * invalid are surfaced to the caller so they can be dropped.
 *
 * <p>The batching and response-scanning are pure static helpers so they can be
 * unit-tested without a network call.
 */
@Component
@Slf4j
public class ExpoPushClient {

    public static final int MAX_MESSAGES_PER_REQUEST = 100;

    public record PushMessage(String to, String title, String body, Map<String, Object> data) {}

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${bingo.expo.push-url:https://exp.host/--/api/v2/push/send}")
    private String expoUrl;

    /** Sends all messages (batched) and returns the tokens Expo rejected as invalid. */
    public List<String> send(List<PushMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<String> invalid = new ArrayList<>();
        for (int from = 0; from < messages.size(); from += MAX_MESSAGES_PER_REQUEST) {
            int to = Math.min(from + MAX_MESSAGES_PER_REQUEST, messages.size());
            invalid.addAll(sendBatch(messages.subList(from, to)));
        }
        return invalid;
    }

    private List<String> sendBatch(List<PushMessage> batch) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(expoUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(batch)))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Expo push service returned {}: {}", response.statusCode(), abbreviate(response.body()));
                return List.of();
            }
            return collectInvalidTokens(response.body(), batch.stream().map(PushMessage::to).toList());
        } catch (IOException | InterruptedException e) {
            log.warn("Expo push send failed: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** Renders the Expo push request body for a batch of messages. */
    static String buildBody(List<PushMessage> messages) {
        try {
            List<Map<String, Object>> entries = messages.stream().map(m -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("to", m.to());
                entry.put("title", m.title());
                entry.put("body", m.body());
                entry.put("sound", "default");
                if (m.data() != null && !m.data().isEmpty()) {
                    entry.put("data", m.data());
                }
                return entry;
            }).toList();
            return OBJECT_MAPPER.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not build Expo push body", e);
        }
    }

    /**
     * Scans a response array and returns the request tokens whose entry has
     * {@code status: "error"} (e.g. {@code DeviceNotRegistered}). Entries line
     * up with the request order by index.
     */
    static List<String> collectInvalidTokens(String responseBody, List<String> tokens) {
        if (responseBody == null || responseBody.isBlank() || tokens == null) {
            return List.of();
        }
        List<String> invalid = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            if (!root.isArray()) {
                return List.of();
            }
            for (int i = 0; i < root.size() && i < tokens.size(); i++) {
                JsonNode entry = root.get(i);
                if (entry != null && "error".equals(entry.path("status").asText())) {
                    invalid.add(tokens.get(i));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Expo push response: {}", e.getMessage());
        }
        return invalid;
    }

    private static String abbreviate(String value) {
        return value == null ? "" : (value.length() > 500 ? value.substring(0, 500) + "…" : value);
    }
}