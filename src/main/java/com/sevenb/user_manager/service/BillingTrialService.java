package com.sevenb.user_manager.service;

import com.sevenb.user_manager.client.BillingTrialClient;
import com.sevenb.user_manager.client.BillingTrialRequest;
import com.sevenb.user_manager.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Pide a centauro-billing el trial de un comercio recien registrado.
 * La llamada sale despues del commit y en segundo plano: nunca hace fallar ni demora el registro.
 */
public class BillingTrialService {

    static final String SOURCE = "USER_MANAGER_REGISTER";
    private static final int MAX_FIELD_LENGTH = 255;
    private static final Logger log = LoggerFactory.getLogger(BillingTrialService.class);

    private final BillingTrialClient client;
    private final ExecutorService executor;

    /**
     * @param client   {@code null} si la integracion esta deshabilitada
     * @param executor executor acotado donde corren las llamadas (y sus reintentos)
     */
    public BillingTrialService(BillingTrialClient client, ExecutorService executor) {
        this.client = client;
        this.executor = executor;
    }

    public boolean isEnabled() {
        return client != null;
    }

    public void requestTrialAfterCommit(Person person) {
        if (!isEnabled()) {
            return;
        }
        try {
            String tenantId = person.getTenantId();
            BillingTrialRequest request = new BillingTrialRequest(
                    companyName(person), truncate(blankToNull(person.getEmail())), SOURCE);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submit(tenantId, request);
                    }
                });
            } else {
                submit(tenantId, request);
            }
        } catch (RuntimeException e) {
            log.error("No se pudo programar la creacion del trial en billing para el tenant {}: "
                    + "trial no creado en billing, crear desde el backoffice", person.getTenantId(), e);
        }
    }

    private void submit(String tenantId, BillingTrialRequest request) {
        try {
            executor.execute(() -> createTrial(tenantId, request));
        } catch (RejectedExecutionException e) {
            log.error("Cola de billing llena o cerrada para el tenant {}: "
                    + "trial no creado en billing, crear desde el backoffice", tenantId);
        }
    }

    private void createTrial(String tenantId, BillingTrialRequest request) {
        try {
            BillingTrialClient.Outcome outcome = client.createTrial(tenantId, request);
            switch (outcome) {
                case CREATED -> log.info("Trial creado en billing para el tenant {}", tenantId);
                case ALREADY_EXISTS -> log.info("El trial ya existia en billing para el tenant {}", tenantId);
                case FAILED -> log.error("Tenant {}: trial no creado en billing, crear desde el backoffice", tenantId);
            }
        } catch (RuntimeException e) {
            log.error("Tenant {}: trial no creado en billing, crear desde el backoffice", tenantId, e);
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static String companyName(Person person) {
        String first = blankToNull(person.getFirstName());
        String last = blankToNull(person.getLastName());
        String name = first == null ? last : (last == null ? first : first + " " + last);
        return truncate(name);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_FIELD_LENGTH ? value : value.substring(0, MAX_FIELD_LENGTH);
    }
}
