package com.sevenb.user_manager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;



@Service
public class SubscriptionService {

    private final RestTemplate restTemplate;

    @Value("${subscriptions.service.url}")
    private String subscriptionServiceUrl;

    public SubscriptionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void subscribeFreePlan(Long userId) {
        String url = subscriptionServiceUrl + "/api/subscriptions/" + userId;

        // JSON como String
        String jsonBody = "{ \"planId\": 2 }";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Si querés manejar cookies manualmente, agregá esto:
        // headers.add("Cookie", "JSESSIONID=XXXXXXX");

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("Suscripción free creada correctamente.");
            } else {
                System.err.println("Error al suscribir: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Excepción al crear suscripción: " + e.getMessage());
        }
    }
}
