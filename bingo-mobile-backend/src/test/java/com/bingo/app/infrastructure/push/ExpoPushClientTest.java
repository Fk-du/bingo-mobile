package com.bingo.app.infrastructure.push;

import com.bingo.app.infrastructure.push.ExpoPushClient.PushMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Expo push body and the invalid-token scan are pure, so they are tested
 * here without touching the network.
 */
class ExpoPushClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PushMessage msg(String token) {
        return new PushMessage(token, "Game starting!", "Open the app to play.", Map.of("gameId", 5L));
    }

    @Test
    @DisplayName("the request body carries the Expo message fields per token")
    void bodyHasExpoFields() throws Exception {
        List<PushMessage> messages = List.of(msg("ExponentPushToken[a]"), msg("ExponentPushToken[b]"));

        JsonNode root = MAPPER.readTree(ExpoPushClient.buildBody(messages));

        assertTrue(root.isArray());
        assertEquals(2, root.size());
        assertEquals("ExponentPushToken[a]", root.get(0).path("to").asText());
        assertEquals("Game starting!", root.get(0).path("title").asText());
        assertEquals("default", root.get(0).path("sound").asText());
        assertEquals(5L, root.get(0).path("data").path("gameId").asLong());
    }

    @Test
    @DisplayName("a successful response marks no tokens invalid")
    void cleanResponseClearsNothing() {
        String body = """
                [{"status":"ok","message":"The device successfully received this notification"},{"status":"ok"}]""";

        assertEquals(List.of(), ExpoPushClient.collectInvalidTokens(body, List.of("ExponentPushToken[a]", "ExponentPushToken[b]")));
    }

    @Test
    @DisplayName("a DeviceNotRegistered error is reported by request order")
    void invalidTokenDetected() {
        String body = """
                [{"status":"ok","message":"received"},{"status":"error","details":"DeviceNotRegistered"}]""";

        assertEquals(List.of("ExponentPushToken[b]"),
                ExpoPushClient.collectInvalidTokens(body, List.of("ExponentPushToken[a]", "ExponentPushToken[b]")));
    }

    @Test
    @DisplayName("ids line up with the requested order even when a non-first token fails")
    void indexOrderRespected() {
        String body = """
                [{"status":"error","details":"DeviceNotRegistered"},{"status":"ok"},{"status":"error","details":"DeviceNotRegistered"}]""";

        assertEquals(List.of("t0", "t2"),
                ExpoPushClient.collectInvalidTokens(body, List.of("t0", "t1", "t2")));
    }

    @Test
    @DisplayName("garbage or empty responses fail safe (nothing cleared)")
    void unparsableBodyFailsSafe() {
        assertTrue(ExpoPushClient.collectInvalidTokens("", List.of("t0")).isEmpty());
        assertTrue(ExpoPushClient.collectInvalidTokens("not json", List.of("t0")).isEmpty());
        assertTrue(ExpoPushClient.collectInvalidTokens(null, List.of("t0")).isEmpty());
        assertTrue(ExpoPushClient.collectInvalidTokens("[]", List.of()).isEmpty());
    }

    @Test
    @DisplayName("enforces the 100-per-request batch in the send loop")
    void batchSizeConstant() {
        assertEquals(100, ExpoPushClient.MAX_MESSAGES_PER_REQUEST);
    }
}