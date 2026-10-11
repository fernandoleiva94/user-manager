package com.sevenb.user_manager.service;

import com.sevenb.user_manager.client.BillingTrialClient;
import com.sevenb.user_manager.client.BillingTrialRequest;
import com.sevenb.user_manager.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(OutputCaptureExtension.class)
class BillingTrialServiceTest {

    private static final String URL = "http://billing.test/internal/v1/tenants/tenant-1/trial";
    private static final List<Duration> BACKOFF =
            List.of(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30));
    private static final String NOT_CREATED = "Tenant tenant-1: trial no creado en billing, crear desde el backoffice";

    private final ManualScheduler scheduler = new ManualScheduler();
    private MockRestServiceServer server;
    private BillingTrialService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BillingTrialClient client = new BillingTrialClient(builder, "http://billing.test", "clave");
        service = new BillingTrialService(client, scheduler.executor, BACKOFF, 100, Duration.ofSeconds(5));
    }

    private static Person person(String tenantId) {
        Person person = new Person();
        person.setTenantId(tenantId);
        person.setFirstName("Juan");
        person.setLastName("Perez");
        person.setEmail("empresa@test.com");
        return person;
    }

    private void request(Person person) {
        service.requestTrialAfterCommit(person, BillingTrialService.SOURCE_REGISTER);
        scheduler.runAll();
    }

    @Test
    void disabledIntegrationDoesNothing() {
        BillingTrialService disabled = BillingTrialService.disabled();

        assertThatCode(() -> disabled.requestTrialAfterCommit(person("tenant-1"), "X")).doesNotThrowAnyException();
        assertThat(disabled.isEnabled()).isFalse();
        disabled.shutdown();
    }

    @Test
    void sendsMappedBodyWithSource() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.companyName").value("Juan Perez"))
                .andExpect(jsonPath("$.billingEmail").value("empresa@test.com"))
                .andExpect(jsonPath("$.source").value("USER_MANAGER_REGISTER"))
                .andRespond(withStatus(HttpStatus.CREATED));

        request(person("tenant-1"));

        server.verify();
        assertThat(service.inFlightTenants()).isEmpty();
    }

    @Test
    void serverErrorIsRetriedWithBackoffAndThenGivesUp(CapturedOutput output) {
        server.expect(ExpectedCount.times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        request(person("tenant-1"));

        server.verify();
        assertThat(scheduler.delays).containsExactly(Duration.ZERO,
                Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30));
        assertThat(output).contains(NOT_CREATED);
        assertThat(service.inFlightTenants()).isEmpty();
    }

    @Test
    void tooManyRequestsAndNetworkErrorsAreRetried() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

        request(person("tenant-1"));

        server.verify();
        assertThat(scheduler.delays).containsExactly(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(10));
    }

    @Test
    void unauthorizedIsNotRetried(CapturedOutput output) {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        request(person("tenant-1"));

        server.verify();
        assertThat(scheduler.delays).containsExactly(Duration.ZERO);
        assertThat(output).contains(NOT_CREATED);
    }

    @Test
    void unexpectedClientExceptionFailsImmediately(CapturedOutput output) {
        BillingTrialClient client = mock(BillingTrialClient.class);
        when(client.createTrial(anyString(), any())).thenThrow(new IllegalStateException("boom"));
        BillingTrialService withMock = new BillingTrialService(client, scheduler.executor, BACKOFF, 100, Duration.ZERO);

        withMock.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER);
        scheduler.runAll();

        verify(client, times(1)).createTrial(anyString(), any());
        assertThat(scheduler.delays).containsExactly(Duration.ZERO);
        assertThat(output).contains(NOT_CREATED);
    }

    @Test
    void badRequestIsRetriedOnceWithoutBillingEmail() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.billingEmail").value("empresa@test.com"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.billingEmail").doesNotExist())
                .andExpect(jsonPath("$.companyName").value("Juan Perez"))
                .andRespond(withStatus(HttpStatus.CREATED));

        request(person("tenant-1"));

        server.verify();
    }

    @Test
    void secondBadRequestGivesUp(CapturedOutput output) {
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        request(person("tenant-1"));

        server.verify();
        assertThat(output).contains(NOT_CREATED);
    }

    @Test
    void badRequestWithoutEmailIsNotRetried() {
        Person person = person("tenant-1");
        person.setEmail(null);
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        request(person);

        server.verify();
    }

    @Test
    void invalidEmailIsNotSent() {
        Person person = person("tenant-1");
        person.setEmail("no-es-un-email");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.billingEmail").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));

        request(person);

        server.verify();
    }

    @Test
    void emailValidation() {
        assertThat(BillingTrialService.billingEmail(" empresa@test.com ")).isEqualTo("empresa@test.com");
        assertThat(BillingTrialService.billingEmail("a b@test.com")).isNull();
        assertThat(BillingTrialService.billingEmail("sin-arroba.com")).isNull();
        assertThat(BillingTrialService.billingEmail("x@sinpunto")).isNull();
        assertThat(BillingTrialService.billingEmail("")).isNull();
        assertThat(BillingTrialService.billingEmail("a".repeat(250) + "@test.com")).isNull();
    }

    @Test
    void companyNameFallsBackToFirstName() {
        Person person = person("tenant-1");
        person.setLastName(" ");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.companyName").value("Juan"))
                .andRespond(withStatus(HttpStatus.CREATED));

        request(person);

        server.verify();
    }

    @Test
    void committedTransactionCallsBillingOnce() {
        FakeTransactionManager tm = new FakeTransactionManager(false);
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.CREATED));

        new TransactionTemplate(tm).executeWithoutResult(status -> {
            service.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER);
            assertThat(scheduler.pendingCount()).isZero();
        });
        scheduler.runAll();

        assertThat(tm.commits).isEqualTo(1);
        server.verify();
    }

    @Test
    void rolledBackTransactionNeverCallsBilling() {
        FakeTransactionManager tm = new FakeTransactionManager(false);

        new TransactionTemplate(tm).executeWithoutResult(status -> {
            service.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER);
            status.setRollbackOnly();
        });

        assertThat(tm.rollbacks).isEqualTo(1);
        verifyNoInteractions(scheduler.executor);
        assertThat(service.inFlightTenants()).isEmpty();
    }

    @Test
    void failedCommitNeverCallsBilling() {
        FakeTransactionManager tm = new FakeTransactionManager(true);

        assertThatThrownBy(() -> new TransactionTemplate(tm).executeWithoutResult(status ->
                service.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER)))
                .isInstanceOf(TransactionSystemException.class);

        verifyNoInteractions(scheduler.executor);
    }

    @Test
    void afterCommitErrorDoesNotEscapeTheCommit(CapturedOutput output) {
        ScheduledExecutorService broken = mock(ScheduledExecutorService.class);
        when(broken.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenThrow(new IllegalStateException("scheduler roto"));
        BillingTrialService withBroken = new BillingTrialService(
                mock(BillingTrialClient.class), broken, BACKOFF, 100, Duration.ZERO);
        FakeTransactionManager tm = new FakeTransactionManager(false);

        assertThatCode(() -> new TransactionTemplate(tm).executeWithoutResult(status ->
                withBroken.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER)))
                .doesNotThrowAnyException();
        assertThat(tm.commits).isEqualTo(1);
        assertThat(output).contains(NOT_CREATED);
    }

    @Test
    void rejectedScheduleIsLoggedWithTenant(CapturedOutput output) {
        ScheduledExecutorService closed = mock(ScheduledExecutorService.class);
        when(closed.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RejectedExecutionException());
        BillingTrialService withClosed = new BillingTrialService(
                mock(BillingTrialClient.class), closed, BACKOFF, 100, Duration.ZERO);

        assertThatCode(() -> withClosed.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER))
                .doesNotThrowAnyException();
        assertThat(output).contains(NOT_CREATED);
        assertThat(withClosed.inFlightTenants()).isEmpty();
    }

    @Test
    void tenantsInFlightAreBounded(CapturedOutput output) {
        BillingTrialService bounded = new BillingTrialService(
                mock(BillingTrialClient.class), scheduler.executor, BACKOFF, 1, Duration.ZERO);

        bounded.requestTrialAfterCommit(person("tenant-0"), BillingTrialService.SOURCE_REGISTER);
        bounded.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER);

        assertThat(scheduler.pendingCount()).isEqualTo(1);
        assertThat(bounded.inFlightTenants()).containsExactly("tenant-0");
        assertThat(output).contains(NOT_CREATED);
    }

    @Test
    void shutdownWaitsThenForcesAndLogsPendingTenants(CapturedOutput output) throws InterruptedException {
        when(scheduler.executor.awaitTermination(5000L, TimeUnit.MILLISECONDS)).thenReturn(false);
        service.requestTrialAfterCommit(person("tenant-1"), BillingTrialService.SOURCE_REGISTER);
        service.requestTrialAfterCommit(person("tenant-2"), BillingTrialService.SOURCE_REGISTER);

        service.shutdown();

        var order = org.mockito.Mockito.inOrder(scheduler.executor);
        order.verify(scheduler.executor).shutdown();
        order.verify(scheduler.executor).awaitTermination(5000L, TimeUnit.MILLISECONDS);
        order.verify(scheduler.executor).shutdownNow();
        assertThat(output).contains(NOT_CREATED)
                .contains("Tenant tenant-2: trial no creado en billing, crear desde el backoffice");
        assertThat(service.inFlightTenants()).isEmpty();
    }

    @Test
    void cleanShutdownDoesNotForce() throws InterruptedException {
        when(scheduler.executor.awaitTermination(5000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        service.shutdown();

        verify(scheduler.executor, times(0)).shutdownNow();
    }

    @Test
    void requestWithoutEmailCopy() {
        BillingTrialRequest request = new BillingTrialRequest("A", "a@b.com", "S");
        assertThat(request.withoutBillingEmail()).isEqualTo(new BillingTrialRequest("A", null, "S"));
    }
}
