package com.sevenb.user_manager.configuration;

import com.sevenb.user_manager.service.BillingTrialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class BillingConfigTest {

    private final BillingConfig config = new BillingConfig();

    private BillingTrialService build(boolean enabled, String url, String apiKey,
                                      String connect, String read, String backoff, String shutdown) {
        BillingTrialService service = config.billingTrialService(enabled, url, apiKey, connect, read, backoff, shutdown);
        service.shutdown();
        return service;
    }

    private BillingTrialService build(String url, String apiKey) {
        return build(true, url, apiKey, "3s", "5s", "2s,10s,30s", "10s");
    }

    @Test
    void missingUrlOrApiKeyDisablesIntegration(CapturedOutput output) {
        assertThat(build("", "clave").isEnabled()).isFalse();
        assertThat(build("http://billing", " ").isEnabled()).isFalse();
        assertThat(output).contains("Integracion con billing deshabilitada");
    }

    @Test
    void flagDisablesIntegration() {
        assertThat(build(false, "http://billing", "clave", "3s", "5s", "2s", "10s").isEnabled()).isFalse();
    }

    @Test
    void fullConfigurationEnablesIntegration() {
        assertThat(build(true, "http://billing", "clave", "3s", "5000ms", "2s", "10s").isEnabled()).isTrue();
    }

    @Test
    void apiKeyIsTrimmed() {
        assertThat(build("http://billing", "  clave-ok \n").isEnabled()).isTrue();
    }

    @Test
    void apiKeyWithInternalSpaceOrControlCharDisablesWithoutLoggingIt(CapturedOutput output) {
        assertThat(build("http://billing", "clave con-espacio").isEnabled()).isFalse();
        assertThat(build("http://billing", "clave\u0007control").isEnabled()).isFalse();
        assertThat(output).contains("BILLING_INTERNAL_API_KEY tiene espacios o caracteres de control")
                .doesNotContain("con-espacio")
                .doesNotContain("\u0007control");
    }

    @Test
    void invalidDurationsDisableInsteadOfFailingStartup(CapturedOutput output) {
        assertThat(build(true, "http://billing", "clave", "tres", "5s", "2s", "10s").isEnabled()).isFalse();
        assertThat(build(true, "http://billing", "clave", "3s", "", "2s", "10s").isEnabled()).isFalse();
        assertThat(build(true, "http://billing", "clave", "3s", "5s", "2s,x", "10s").isEnabled()).isFalse();
        assertThat(build(true, "http://billing", "clave", "3s", "5s", "2s", "-1s").isEnabled()).isFalse();
        assertThat(output).contains("formato invalido");
    }

    @Test
    void parsesBackoff() {
        assertThat(BillingConfig.parseBackoff("2s, 10s,30s"))
                .containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30));
        assertThat(BillingConfig.parseBackoff("")).isEmpty();
    }
}
