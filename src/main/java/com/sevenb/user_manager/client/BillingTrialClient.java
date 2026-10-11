package com.sevenb.user_manager.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente de la API interna de centauro-billing para crear el trial de un tenant.
 * Hace un solo intento por llamada; los reintentos los programa {@code BillingTrialService}.
 */
public class BillingTrialClient {

    static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final Logger log = LoggerFactory.getLogger(BillingTrialClient.class);

    public enum Result {
        CREATED,
        ALREADY_EXISTS,
        /** Error de red, 5xx o 429: se puede reintentar. */
        RETRYABLE,
        /** 400: el body fue rechazado. */
        BAD_REQUEST,
        /** Cualquier otro error: no se reintenta. */
        FAILED
    }

    private final RestClient restClient;
    private final String apiKey;

    public BillingTrialClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.restClient = builder.baseUrl(stripTrailingSlash(baseUrl)).build();
        this.apiKey = apiKey;
    }

    public Result createTrial(String tenantId, BillingTrialRequest request) {
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/internal/v1/tenants/{tenantId}/trial", tenantId)
                    .header(API_KEY_HEADER, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            int status = response.getStatusCode().value();
            if (status == 201) {
                return Result.CREATED;
            }
            if (response.getStatusCode().is2xxSuccessful()) {
                return Result.ALREADY_EXISTS;
            }
            log.warn("Billing respondio {} al crear el trial del tenant {}", status, tenantId);
            return Result.FAILED;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            log.warn("Billing respondio {} al crear el trial del tenant {}", status.value(), tenantId);
            if (status.is5xxServerError() || status.value() == 429) {
                return Result.RETRYABLE;
            }
            return status.value() == 400 ? Result.BAD_REQUEST : Result.FAILED;
        } catch (ResourceAccessException e) {
            log.warn("Error de comunicacion con billing al crear el trial del tenant {}: {}",
                    tenantId, e.getMessage());
            return Result.RETRYABLE;
        } catch (RuntimeException e) {
            log.warn("Error inesperado al crear el trial del tenant {} en billing: {}",
                    tenantId, e.getClass().getName());
            return Result.FAILED;
        }
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
