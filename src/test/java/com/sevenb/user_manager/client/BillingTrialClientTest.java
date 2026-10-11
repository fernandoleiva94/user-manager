package com.sevenb.user_manager.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(OutputCaptureExtension.class)
class BillingTrialClientTest {

    private static final String URL = "http://billing.test/internal/v1/tenants/tenant-1/trial";
    private static final BillingTrialRequest REQUEST =
            new BillingTrialRequest("Juan Perez", "empresa@test.com", "USER_MANAGER_REGISTER");

    private MockRestServiceServer server;
    private BillingTrialClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BillingTrialClient(builder, "http://billing.test/", "clave-secreta");
    }

    private BillingTrialClient.Result respond(HttpStatus status) {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(status));
        BillingTrialClient.Result result = client.createTrial("tenant-1", REQUEST);
        server.verify();
        return result;
    }

    @Test
    void sendsApiKeyHeaderAndBodyWithoutPlanCode() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "clave-secreta"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.companyName").value("Juan Perez"))
                .andExpect(jsonPath("$.billingEmail").value("empresa@test.com"))
                .andExpect(jsonPath("$.source").value("USER_MANAGER_REGISTER"))
                .andExpect(jsonPath("$.planCode").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Result.CREATED);
        server.verify();
    }

    @Test
    void omitsNullFields() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.companyName").doesNotExist())
                .andExpect(jsonPath("$.billingEmail").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));

        client.createTrial("tenant-1", new BillingTrialRequest(null, null, "USER_MANAGER_REGISTER"));
        server.verify();
    }

    @Test
    void mapsStatusesToResults() {
        assertThat(respond(HttpStatus.OK)).isEqualTo(BillingTrialClient.Result.ALREADY_EXISTS);
        server.reset();
        assertThat(respond(HttpStatus.UNAUTHORIZED)).isEqualTo(BillingTrialClient.Result.FAILED);
        server.reset();
        assertThat(respond(HttpStatus.FORBIDDEN)).isEqualTo(BillingTrialClient.Result.FAILED);
        server.reset();
        assertThat(respond(HttpStatus.BAD_REQUEST)).isEqualTo(BillingTrialClient.Result.BAD_REQUEST);
        server.reset();
        assertThat(respond(HttpStatus.TOO_MANY_REQUESTS)).isEqualTo(BillingTrialClient.Result.RETRYABLE);
        server.reset();
        assertThat(respond(HttpStatus.INTERNAL_SERVER_ERROR)).isEqualTo(BillingTrialClient.Result.RETRYABLE);
        server.reset();
        assertThat(respond(HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo(BillingTrialClient.Result.RETRYABLE);
    }

    @Test
    void networkErrorIsRetryable() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Result.RETRYABLE);
    }

    @Test
    void unexpectedExceptionFailsWithoutLoggingItsMessage(CapturedOutput output) {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IllegalStateException("mensaje-con-datos-sensibles");
        });

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Result.FAILED);
        assertThat(output).contains("java.lang.IllegalStateException").doesNotContain("mensaje-con-datos-sensibles");
    }

    @Test
    void neverLogsApiKey(CapturedOutput output) {
        respond(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(output).doesNotContain("clave-secreta");
    }
}
