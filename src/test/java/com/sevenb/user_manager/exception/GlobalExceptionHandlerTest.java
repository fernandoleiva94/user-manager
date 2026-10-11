package com.sevenb.user_manager.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentIsJson400() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("No podés cambiar tu propio rol"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400)
                .containsEntry("error", "Bad Request")
                .containsEntry("message", "No podés cambiar tu propio rol");
    }

    @Test
    void runtimeExceptionIsJson404() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleRuntimeException(new RuntimeException("Usuario no encontrado: 9"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404)
                .containsEntry("error", "Not Found")
                .containsEntry("message", "Usuario no encontrado: 9");
    }

    @Test
    void accessDeniedIsSpecJson403() {
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(new AccessDeniedException("x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsExactly(
                Map.entry("status", 403),
                Map.entry("error", "Forbidden"),
                Map.entry("message", "No tenés permiso para realizar esta acción"));
    }
}
