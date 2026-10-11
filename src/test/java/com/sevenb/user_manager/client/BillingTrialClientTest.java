package com.sevenb.user_manager.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BillingTrialClientTest {

    private static final String URL = "http://billing.test/internal/v1/tenants/tenant-1/trial";
    private static final List<Duration> BACKOFF =
            List.of(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30));
    private static final BillingTrialRequest REQUEST =
            new BillingTrialRequest("Juan Perez", "empresa@test.com", "USER_MANAGER_REGISTER");

    private final List<Duration> sleeps = new ArrayList<>();
    private MockRestServiceServer server;
    private BillingTrialClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BillingTrialClient(builder, "http://billing.test/", "clave-secreta", BACKOFF, sleeps::add);
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

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.CREATED);
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void omitsNullFields() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.companyName").doesNotExist())
                .andExpect(jsonPath("$.billingEmail").doesNotExist())
                .andExpect(jsonPath("$.source").value("USER_MANAGER_REGISTER"))
                .andRespond(withStatus(HttpStatus.CREATED));

        client.createTrial("tenant-1", new BillingTrialRequest(null, null, "USER_MANAGER_REGISTER"));
        server.verify();
    }

    @Test
    void okMeansTrialAlreadyExisted() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.ALREADY_EXISTS);
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void unauthorizedIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.FAILED);
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void otherClientErrorsAreNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.FAILED);
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void serverErrorIsRetriedWithBackoffAndThenGivesUp() {
        server.expect(ExpectedCount.times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.FAILED);
        server.verify();
        assertThat(sleeps).containsExactlyElementsOf(BACKOFF);
    }

    @Test
    void tooManyRequestsIsRetried() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CREATED));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.CREATED);
        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void networkErrorIsRetried() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

        assertThat(client.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.ALREADY_EXISTS);
        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(10));
    }

    @Test
    void interruptedBackoffGivesUp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer local = MockRestServiceServer.bindTo(builder).build();
        BillingTrialClient interrupted = new BillingTrialClient(builder, "http://billing.test", "k", BACKOFF,
                d -> { throw new InterruptedException(); });
        local.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(interrupted.createTrial("tenant-1", REQUEST)).isEqualTo(BillingTrialClient.Outcome.FAILED);
        assertThat(Thread.interrupted()).isTrue();
        local.verify();
    }
}
