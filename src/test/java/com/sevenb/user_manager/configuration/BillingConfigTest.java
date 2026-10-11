package com.sevenb.user_manager.configuration;

import com.sevenb.user_manager.service.BillingTrialService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BillingConfigTest {

    private final BillingConfig config = new BillingConfig();

    @Test
    void missingUrlDisablesIntegration() {
        BillingTrialService service = config.billingTrialService(true, "", "clave", "3s", "5s", "2s,10s,30s");
        assertThat(service.isEnabled()).isFalse();
        service.shutdown();
    }

    @Test
    void missingApiKeyDisablesIntegration() {
        BillingTrialService service = config.billingTrialService(true, "http://billing", " ", "3s", "5s", "2s");
        assertThat(service.isEnabled()).isFalse();
        service.shutdown();
    }

    @Test
    void flagDisablesIntegration() {
        BillingTrialService service = config.billingTrialService(false, "http://billing", "clave", "3s", "5s", "2s");
        assertThat(service.isEnabled()).isFalse();
        service.shutdown();
    }

    @Test
    void fullConfigurationEnablesIntegration() {
        BillingTrialService service = config.billingTrialService(true, "http://billing", "clave", "3s", "5000ms", "2s");
        assertThat(service.isEnabled()).isTrue();
        service.shutdown();
    }

    @Test
    void parsesBackoff() {
        assertThat(BillingConfig.parseBackoff("2s, 10s,30s"))
                .containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30));
        assertThat(BillingConfig.parseBackoff("")).isEmpty();
    }
}
