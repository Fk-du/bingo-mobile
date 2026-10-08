package com.bingo.app.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The envelope used by the advice, the security filters and the entry points. */
class ErrorResponsesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bodyCarriesTheFullEnvelope() {
        Map<String, Object> body = ErrorResponses.body(HttpStatus.NOT_FOUND, "Game not found",
                "Game not found", "not_found");

        assertThat(body).containsEntry("success", false)
                .containsEntry("message", "Game not found")
                .containsEntry("userMessage", "Game not found")
                .containsEntry("code", "not_found")
                .containsEntry("status", 404);
        assertThat(body).containsKey("timestamp");
    }

    @Test
    void writeSerializesEnvelopeAsJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ErrorResponses.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                "Too many requests. Please slow down and try again shortly.", "rate_limited");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains("application/json");

        JsonNode json = mapper.readTree(response.getContentAsByteArray());
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("code").asText()).isEqualTo("rate_limited");
        assertThat(json.get("status").asInt()).isEqualTo(429);
        assertThat(json.get("userMessage").asText())
                .isEqualTo("Too many requests. Please slow down and try again shortly.");
        assertThat(json.has("timestamp")).isTrue();
    }
}
