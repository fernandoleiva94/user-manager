package com.sevenb.user_manager.configuration;

import com.sevenb.user_manager.client.BillingTrialClient;
import com.sevenb.user_manager.service.BillingTrialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class BillingConfig {

    private static final Logger log = LoggerFactory.getLogger(BillingConfig.class);
    private static final int SCHEDULER_THREADS = 2;
    private static final int MAX_TENANTS_IN_FLIGHT = 100;

    @Bean(destroyMethod = "shutdown")
    public BillingTrialService billingTrialService(
            @Value("${billing.trial.enabled:true}") boolean enabled,
            @Value("${billing.url:}") String url,
            @Value("${billing.internal-api-key:}") String rawApiKey,
            @Value("${billing.connect-timeout:3s}") String connectTimeout,
            @Value("${billing.read-timeout:5s}") String readTimeout,
            @Value("${billing.trial.retry-backoff:2s,10s,30s}") String retryBackoff,
            @Value("${billing.trial.shutdown-timeout:10s}") String shutdownTimeout) {
        if (!enabled) {
            return disabled("billing.trial.enabled=false");
        }
        String apiKey = rawApiKey == null ? "" : rawApiKey.trim();
        if (isBlank(url) || apiKey.isEmpty()) {
            return disabled("falta BILLING_URL o BILLING_INTERNAL_API_KEY");
        }
        if (!isValidApiKey(apiKey)) {
            return disabled("BILLING_INTERNAL_API_KEY tiene espacios o caracteres de control");
        }

        Duration connect;
        Duration read;
        Duration shutdown;
        List<Duration> backoff;
        try {
            connect = parseDuration(connectTimeout);
            read = parseDuration(readTimeout);
            shutdown = parseDuration(shutdownTimeout);
            backoff = parseBackoff(retryBackoff);
        } catch (IllegalArgumentException e) {
            return disabled("timeout, backoff o shutdown-timeout con formato invalido");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connect);
        requestFactory.setReadTimeout(read);
        BillingTrialClient client = new BillingTrialClient(
                RestClient.builder().requestFactory(requestFactory), url, apiKey);
        return new BillingTrialService(client, newScheduler(), backoff, MAX_TENANTS_IN_FLIGHT, shutdown);
    }

    private static BillingTrialService disabled(String reason) {
        log.warn("Integracion con billing deshabilitada ({}): las empresas nuevas no tendran trial automatico",
                reason);
        return BillingTrialService.disabled();
    }

    static boolean isValidApiKey(String apiKey) {
        return apiKey.chars().noneMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c));
    }

    static List<Duration> parseBackoff(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BillingConfig::parseDuration)
                .toList();
    }

    private static Duration parseDuration(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Duracion vacia");
        }
        Duration duration = DurationStyle.detectAndParse(value.trim());
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duracion negativa");
        }
        return duration;
    }

    private static ScheduledThreadPoolExecutor newScheduler() {
        AtomicInteger counter = new AtomicInteger();
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(SCHEDULER_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "billing-trial-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
