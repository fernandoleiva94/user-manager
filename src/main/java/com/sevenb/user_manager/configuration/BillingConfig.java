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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class BillingConfig {

    private static final Logger log = LoggerFactory.getLogger(BillingConfig.class);
    private static final int EXECUTOR_THREADS = 2;
    private static final int EXECUTOR_QUEUE_CAPACITY = 100;

    @Bean(destroyMethod = "shutdown")
    public BillingTrialService billingTrialService(
            @Value("${billing.trial.enabled:true}") boolean enabled,
            @Value("${billing.url:}") String url,
            @Value("${billing.internal-api-key:}") String apiKey,
            @Value("${billing.connect-timeout:3s}") String connectTimeout,
            @Value("${billing.read-timeout:5s}") String readTimeout,
            @Value("${billing.trial.retry-backoff:2s,10s,30s}") String retryBackoff) {
        if (!enabled) {
            log.warn("Integracion con billing deshabilitada (billing.trial.enabled=false): "
                    + "los comercios nuevos no tendran trial automatico");
            return new BillingTrialService(null, null);
        }
        if (isBlank(url) || isBlank(apiKey)) {
            log.warn("Integracion con billing deshabilitada: falta BILLING_URL o BILLING_INTERNAL_API_KEY. "
                    + "Los comercios nuevos no tendran trial automatico");
            return new BillingTrialService(null, null);
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DurationStyle.detectAndParse(connectTimeout));
        requestFactory.setReadTimeout(DurationStyle.detectAndParse(readTimeout));

        BillingTrialClient client = new BillingTrialClient(
                RestClient.builder().requestFactory(requestFactory),
                url, apiKey, parseBackoff(retryBackoff), duration -> Thread.sleep(duration.toMillis()));
        return new BillingTrialService(client, newExecutor());
    }

    static List<Duration> parseBackoff(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DurationStyle::detectAndParse)
                .toList();
    }

    private static ThreadPoolExecutor newExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return new ThreadPoolExecutor(EXECUTOR_THREADS, EXECUTOR_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(EXECUTOR_QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "billing-trial-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
