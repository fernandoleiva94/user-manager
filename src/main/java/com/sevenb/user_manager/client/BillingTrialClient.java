package com.sevenb.user_manager.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * Cliente de la API interna de centauro-billing para crear el trial de un tenant.
 * Reintenta errores de red, 5xx y 429 con el backoff configurado; el resto de los 4xx
 * (incluido 401) no se reintenta.
 */
public class BillingTrialClient {

    static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final Logger log = LoggerFactory.getLogger(BillingTrialClient.class);

    public enum Outcome { CREATED, ALREADY_EXISTS, FAILED }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RestClient restClient;
    private final String apiKey;
    private final List<Duration> retryBackoff;
    private final Sleeper sleeper;

    public BillingTrialClient(RestClient.Builder builder, String baseUrl, String apiKey,
                              List<Duration> retryBackoff, Sleeper sleeper) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
        this.apiKey = apiKey;
        this.retryBackoff = List.copyOf(retryBackoff);
        this.sleeper = sleeper;
    }

    public Outcome createTrial(String tenantId, BillingTrialRequest request) {
        int maxAttempts = retryBackoff.size() + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean retryable;
            try {
                ResponseEntity<Void> response = restClient.post()
                        .uri("/internal/v1/tenants/{tenantId}/trial", tenantId)
                        .header(API_KEY_HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                if (response.getStatusCode().value() == 201) {
                    return Outcome.CREATED;
                }
                if (response.getStatusCode().is2xxSuccessful()) {
                    return Outcome.ALREADY_EXISTS;
                }
                log.warn("Billing respondio {} al crear el trial del tenant {} (intento {}/{})",
                        response.getStatusCode().value(), tenantId, attempt, maxAttempts);
                retryable = false;
            } catch (RestClientResponseException e) {
                HttpStatusCode status = e.getStatusCode();
                retryable = status.is5xxServerError() || status.value() == 429;
                log.warn("Billing respondio {} al crear el trial del tenant {} (intento {}/{})",
                        status.value(), tenantId, attempt, maxAttempts);
            } catch (RuntimeException e) {
                retryable = true;
                log.warn("Error de comunicacion con billing al crear el trial del tenant {} (intento {}/{}): {}",
                        tenantId, attempt, maxAttempts, e.getMessage());
            }

            if (!retryable || attempt == maxAttempts) {
                return Outcome.FAILED;
            }
            try {
                sleeper.sleep(retryBackoff.get(attempt - 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Outcome.FAILED;
            }
        }
        return Outcome.FAILED;
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
