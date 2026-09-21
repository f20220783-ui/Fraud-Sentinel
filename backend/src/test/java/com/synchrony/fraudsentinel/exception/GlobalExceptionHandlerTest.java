package com.synchrony.fraudsentinel.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgument_returns400WithoutLeakingInternalMessage() {
        IllegalArgumentException ex = new IllegalArgumentException(
                "internal detail that should never reach the client");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("errorCode", "BAD_REQUEST");
        assertThat(response.getBody().get("message").toString())
                .doesNotContain("internal detail");
    }

    @Test
    void securityException_returns403() {
        SecurityException ex = new SecurityException("unauthorized access attempt");

        ResponseEntity<Map<String, Object>> response = handler.handleSecurity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "FORBIDDEN");
    }

    @Test
    void genericException_returns500AndDoesNotLeakStackTrace() {
        RuntimeException ex = new RuntimeException("database connection string: postgres://secret");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message").toString())
                .doesNotContain("postgres://secret");
    }

    @Test
    void everyErrorResponse_includesTimestamp() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneric(new RuntimeException("x"));

        assertThat(response.getBody()).containsKey("timestamp");
    }
}
