package com.bingo.app.common.exception;

import com.bingo.app.master.exception.InviteRegistrationException;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.exception.WalletException;
import com.bingo.app.infrastructure.security.PhoneAuthException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every exception type must come out as the shared envelope with a real HTTP
 * status — no bare Spring Boot default bodies, no internal messages leaking
 * on 5xx.
 */
class ApiExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/t/not-found")
        void notFound() {
            throw new NotFoundException("Game not found");
        }

        @GetMapping("/t/bad-request")
        void badRequest() {
            throw new BadRequestException("Game is not in progress");
        }

        @GetMapping("/t/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("Unknown status: weird");
        }

        @GetMapping("/t/forbidden")
        void forbidden() {
            throw new ForbiddenException("Game does not belong to this admin");
        }

        @GetMapping("/t/conflict")
        void conflict() {
            throw new ConflictException("Number already called");
        }

        @GetMapping("/t/wallet")
        void wallet() {
            throw new WalletException("Insufficient balance", "You do not have enough balance.");
        }

        @GetMapping("/t/domain")
        void domain() {
            throw new GameProgressException("Game is not in progress", "The game has already ended.");
        }

        @GetMapping("/t/invite")
        void invite() {
            throw InviteRegistrationException.invalidCode();
        }

        @GetMapping("/t/no-password")
        void noPassword() {
            throw PhoneAuthException.noPassword();
        }

        @GetMapping("/t/boom")
        void boom() {
            throw new RuntimeException("secret internal detail: NPE at GameEngineService.java:42");
        }

        @GetMapping("/t/db-down")
        void dbDown() {
            throw new DataAccessResourceFailureException("connection refused at 10.0.0.5:5432");
        }

        @GetMapping("/t/missing-param")
        void missingParam(@RequestParam String name) {
        }

        @PostMapping("/t/valid")
        void valid(@Valid @RequestBody Payload payload) {
        }
    }

    static class Payload {
        @NotBlank
        public String name;
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void notFoundReturns404Envelope() throws Exception {
        mockMvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Game not found"))
                .andExpect(jsonPath("$.userMessage").value("Game not found"))
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void badRequestReturns400Envelope() throws Exception {
        mockMvc.perform(get("/t/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.userMessage").value("Game is not in progress"));
    }

    @Test
    void illegalArgumentReturns400Not500() throws Exception {
        mockMvc.perform(get("/t/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Unknown status: weird"));
    }

    @Test
    void forbiddenReturns403Envelope() throws Exception {
        mockMvc.perform(get("/t/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"))
                .andExpect(jsonPath("$.userMessage").value("Game does not belong to this admin"));
    }

    @Test
    void conflictReturns409Envelope() throws Exception {
        mockMvc.perform(get("/t/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
    }

    @Test
    void walletExceptionKeepsWalletCodeAndUserMessage() throws Exception {
        mockMvc.perform(get("/t/wallet"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("wallet"))
                .andExpect(jsonPath("$.userMessage").value("You do not have enough balance."));
    }

    @Test
    void domainExceptionKeepsCodeAndUserMessage() throws Exception {
        mockMvc.perform(get("/t/domain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("game_progress"))
                .andExpect(jsonPath("$.userMessage").value("The game has already ended."));
    }

    @Test
    void inviteExceptionKeepsInviteCode() throws Exception {
        mockMvc.perform(get("/t/invite"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invite_invalid"))
                .andExpect(jsonPath("$.userMessage")
                        .value("This invite link is invalid. Ask your admin for a new link."));
    }

    @Test
    void noPasswordReturns421() throws Exception {
        mockMvc.perform(get("/t/no-password"))
                .andExpect(status().is(421))
                .andExpect(jsonPath("$.code").value("no_password"));
    }

    @Test
    void unexpectedExceptionReturnsGeneric500WithoutInternals() throws Exception {
        mockMvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.userMessage").value("Something went wrong. Please try again."))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void dataAccessExceptionReturnsGeneric500() throws Exception {
        mockMvc.perform(get("/t/db-down"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("database_error"))
                .andExpect(jsonPath("$.message").value("Database operation failed"));
    }

    @Test
    void missingRequestParameterReturns400() throws Exception {
        mockMvc.perform(get("/t/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_parameter"));
    }

    @Test
    void beanValidationFailureReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/t/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.userMessage").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/t/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }
}
