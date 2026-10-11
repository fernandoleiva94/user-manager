package com.sevenb.user_manager.service;

import com.sevenb.user_manager.client.BillingTrialClient;
import com.sevenb.user_manager.client.BillingTrialRequest;
import com.sevenb.user_manager.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Pide a centauro-billing el trial de una empresa recien creada.
 * La llamada sale despues del commit y en segundo plano: nunca hace fallar ni demora el alta.
 * Los reintentos se reprograman en el scheduler (sin hilos dormidos). No hay conciliacion
 * automatica: si se agotan, el trial se crea desde el backoffice.
 */
public class BillingTrialService {

    public static final String SOURCE_REGISTER = "USER_MANAGER_REGISTER";
    public static final String SOURCE_PLATFORM_ADMIN = "USER_MANAGER_PLATFORM_ADMIN";

    private static final int MAX_FIELD_LENGTH = 255;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Logger log = LoggerFactory.getLogger(BillingTrialService.class);

    private final BillingTrialClient client;
    private final ScheduledExecutorService scheduler;
    private final List<Duration> retryBackoff;
    private final int maxInFlight;
    private final Duration shutdownTimeout;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public BillingTrialService(BillingTrialClient client, ScheduledExecutorService scheduler,
                               List<Duration> retryBackoff, int maxInFlight, Duration shutdownTimeout) {
        this.client = client;
        this.scheduler = scheduler;
        this.retryBackoff = List.copyOf(retryBackoff);
        this.maxInFlight = maxInFlight;
        this.shutdownTimeout = shutdownTimeout;
    }

    public static BillingTrialService disabled() {
        return new BillingTrialService(null, null, List.of(), 0, Duration.ZERO);
    }

    /** Un intento de creacion del trial; si hace falta se reprograma a si mismo. */
    record TrialTask(BillingTrialService service, String tenantId, BillingTrialRequest request,
                     int attempt, boolean retriedWithoutEmail) implements Runnable {
        @Override
        public void run() {
            service.runAttempt(this);
        }
    }

    public boolean isEnabled() {
        return client != null;
    }

    Set<String> inFlightTenants() {
        return Set.copyOf(inFlight);
    }

    public void requestTrialAfterCommit(Person person, String source) {
        if (!isEnabled()) {
            return;
        }
        try {
            String tenantId = person.getTenantId();
            BillingTrialRequest request = new BillingTrialRequest(
                    companyName(person), billingEmail(person.getEmail()), source);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            submit(tenantId, request);
                        } catch (RuntimeException e) {
                            logNotCreated(tenantId, e);
                        }
                    }
                });
            } else {
                submit(tenantId, request);
            }
        } catch (RuntimeException e) {
            logNotCreated(person.getTenantId(), e);
        }
    }

    private void submit(String tenantId, BillingTrialRequest request) {
        if (!inFlight.add(tenantId)) {
            log.info("Ya hay un pedido de trial en curso para el tenant {}", tenantId);
            return;
        }
        if (inFlight.size() > maxInFlight) {
            inFlight.remove(tenantId);
            log.error("Cola de billing llena. Tenant {}: trial no creado en billing, crear desde el backoffice",
                    tenantId);
            return;
        }
        schedule(new TrialTask(this, tenantId, request, 1, false), Duration.ZERO);
    }

    private void runAttempt(TrialTask task) {
        BillingTrialClient.Result result;
        try {
            result = client.createTrial(task.tenantId(), task.request());
        } catch (RuntimeException e) {
            log.warn("Error inesperado al crear el trial del tenant {}: {}", task.tenantId(), e.getClass().getName());
            result = BillingTrialClient.Result.FAILED;
        }

        switch (result) {
            case CREATED -> finish(task, "Trial creado en billing para el tenant {}");
            case ALREADY_EXISTS -> finish(task, "El trial ya existia en billing para el tenant {}");
            case BAD_REQUEST -> {
                if (task.request().billingEmail() != null && !task.retriedWithoutEmail()) {
                    log.warn("Billing rechazo el body del tenant {}; se reintenta sin billingEmail", task.tenantId());
                    schedule(new TrialTask(this, task.tenantId(), task.request().withoutBillingEmail(),
                            task.attempt(), true), Duration.ZERO);
                } else {
                    giveUp(task.tenantId());
                }
            }
            case RETRYABLE -> {
                if (task.attempt() <= retryBackoff.size()) {
                    schedule(new TrialTask(this, task.tenantId(), task.request(), task.attempt() + 1,
                            task.retriedWithoutEmail()), retryBackoff.get(task.attempt() - 1));
                } else {
                    giveUp(task.tenantId());
                }
            }
            case FAILED -> giveUp(task.tenantId());
        }
    }

    private void schedule(TrialTask task, Duration delay) {
        try {
            scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            inFlight.remove(task.tenantId());
            log.error("Scheduler de billing cerrado o saturado. Tenant {}: trial no creado en billing, "
                    + "crear desde el backoffice", task.tenantId());
        }
    }

    private void finish(TrialTask task, String message) {
        inFlight.remove(task.tenantId());
        log.info(message, task.tenantId());
    }

    private void giveUp(String tenantId) {
        inFlight.remove(tenantId);
        log.error("Tenant {}: trial no creado en billing, crear desde el backoffice", tenantId);
    }

    private static void logNotCreated(String tenantId, RuntimeException e) {
        log.error("Tenant {}: trial no creado en billing, crear desde el backoffice ({})",
                tenantId, e.getClass().getName());
    }

    public void shutdown() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (String tenantId : inFlight) {
            log.error("Apagado con pedido pendiente. Tenant {}: trial no creado en billing, "
                    + "crear desde el backoffice", tenantId);
        }
        inFlight.clear();
    }

    private static String companyName(Person person) {
        String first = blankToNull(person.getFirstName());
        String last = blankToNull(person.getLastName());
        String name = first == null ? last : (last == null ? first : first + " " + last);
        return name == null || name.length() <= MAX_FIELD_LENGTH ? name : name.substring(0, MAX_FIELD_LENGTH);
    }

    static String billingEmail(String email) {
        String value = blankToNull(email);
        if (value == null || value.length() > MAX_FIELD_LENGTH || !EMAIL.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
