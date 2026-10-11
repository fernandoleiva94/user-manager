package com.sevenb.user_manager.service;

import com.sevenb.user_manager.client.BillingTrialClient;
import com.sevenb.user_manager.client.BillingTrialRequest;
import com.sevenb.user_manager.entity.Person;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillingTrialServiceTest {

    private final BillingTrialClient client = mock(BillingTrialClient.class);
    private final ExecutorService executor = directExecutor();
    private final BillingTrialService service = new BillingTrialService(client, executor);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ExecutorService directExecutor() {
        ExecutorService executor = mock(ExecutorService.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());
        return executor;
    }

    private static Person person() {
        Person person = new Person();
        person.setTenantId("tenant-1");
        person.setFirstName("Juan");
        person.setLastName("Perez");
        person.setEmail("empresa@test.com");
        return person;
    }

    @Test
    void disabledIntegrationDoesNothing() {
        ExecutorService disabledExecutor = mock(ExecutorService.class);
        BillingTrialService disabled = new BillingTrialService(null, disabledExecutor);

        disabled.requestTrialAfterCommit(person());

        assertThat(disabled.isEnabled()).isFalse();
        verifyNoInteractions(disabledExecutor);
    }

    @Test
    void withoutTransactionCallsImmediatelyWithMappedBody() {
        when(client.createTrial(anyString(), any())).thenReturn(BillingTrialClient.Outcome.CREATED);

        service.requestTrialAfterCommit(person());

        ArgumentCaptor<BillingTrialRequest> body = ArgumentCaptor.forClass(BillingTrialRequest.class);
        verify(client).createTrial(eq("tenant-1"), body.capture());
        assertThat(body.getValue()).isEqualTo(
                new BillingTrialRequest("Juan Perez", "empresa@test.com", "USER_MANAGER_REGISTER"));
    }

    @Test
    void companyNameFallsBackToFirstNameAndBlankEmailIsOmitted() {
        Person person = person();
        person.setLastName(" ");
        person.setEmail("");

        service.requestTrialAfterCommit(person);

        ArgumentCaptor<BillingTrialRequest> body = ArgumentCaptor.forClass(BillingTrialRequest.class);
        verify(client).createTrial(eq("tenant-1"), body.capture());
        assertThat(body.getValue().companyName()).isEqualTo("Juan");
        assertThat(body.getValue().billingEmail()).isNull();
    }

    @Test
    void withTransactionWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service.requestTrialAfterCommit(person());

        verifyNoInteractions(client);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(client).createTrial(eq("tenant-1"), any());
    }

    @Test
    void rollbackNeverCallsBilling() {
        TransactionSynchronizationManager.initSynchronization();

        service.requestTrialAfterCommit(person());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(client);
    }

    @Test
    void clientExceptionIsSwallowed() {
        when(client.createTrial(anyString(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service.requestTrialAfterCommit(person())).doesNotThrowAnyException();
    }

    @Test
    void rejectedExecutionIsSwallowed() {
        ExecutorService full = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException()).when(full).execute(any());
        BillingTrialService saturated = new BillingTrialService(client, full);

        assertThatCode(() -> saturated.requestTrialAfterCommit(person())).doesNotThrowAnyException();
        verify(client, never()).createTrial(anyString(), any());
    }
}
