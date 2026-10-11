package com.sevenb.user_manager.service;

import com.sevenb.user_manager.client.BillingTrialClient;
import com.sevenb.user_manager.client.BillingTrialRequest;
import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegisterServiceTest {

    private final PersonService personService = mock(PersonService.class);
    private final UserService userService = mock(UserService.class);
    private final RoleService roleService = mock(RoleService.class);
    private final BillingTrialClient billingClient = mock(BillingTrialClient.class);
    private final BillingTrialService billingTrialService = new BillingTrialService(billingClient, directExecutor());
    private final RegisterService service =
            new RegisterService(personService, userService, roleService, billingTrialService);

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

    private static RegisterDTO request(String tenantId) {
        Person person = new Person();
        person.setId(99L);
        person.setEmail("empresa@test.com");
        person.setDocumentNumber("20123456789");
        person.setTenantId(tenantId);
        UserEntity user = new UserEntity();
        user.setId(77L);
        user.setUsername("dueno");
        user.setPassword("secreta");
        RegisterDTO dto = new RegisterDTO();
        dto.setPerson(person);
        dto.setUser(user);
        return dto;
    }

    @Test
    void registerGeneratesTenantIdAndIgnoresBody() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleService.getRoleByNameForTenant(anyString(), anyString()))
                .thenReturn(new RoleEntity(1L, null, "ADMIN", null, null));

        service.register(request("tenant-de-otro"));

        ArgumentCaptor<Person> person = ArgumentCaptor.forClass(Person.class);
        verify(personService).createPerson(person.capture());
        String tenantId = person.getValue().getTenantId();
        assertThat(tenantId).isNotEqualTo("tenant-de-otro");
        assertThat(UUID.fromString(tenantId).toString()).isEqualTo(tenantId);
        assertThat(person.getValue().getId()).isNull();
        verify(roleService).seedDefaultRolesForTenant(tenantId);
        verify(roleService).getRoleByNameForTenant("ADMIN", tenantId);
    }

    @Test
    void registerGeneratesTenantIdWhenBodyHasNone() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(request(null));

        ArgumentCaptor<Person> person = ArgumentCaptor.forClass(Person.class);
        verify(personService).createPerson(person.capture());
        assertThat(person.getValue().getTenantId()).isNotBlank();
    }

    @Test
    void registerIgnoresCreatedAtFromBody() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        RegisterDTO dto = request(null);
        dto.getPerson().setCreatedAt(LocalDateTime.of(2000, 1, 1, 0, 0));

        service.register(dto);

        ArgumentCaptor<Person> person = ArgumentCaptor.forClass(Person.class);
        verify(personService).createPerson(person.capture());
        assertThat(person.getValue().getCreatedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void registerNeverReusesUserIdFromBody() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(request(null));

        ArgumentCaptor<UserEntity> user = ArgumentCaptor.forClass(UserEntity.class);
        verify(userService).createUserForRegister(user.capture());
        assertThat(user.getValue().getId()).isNull();
    }

    @Test
    void registerRequestsBillingTrialOnlyAfterCommit() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.register(request(null));

        verifyNoInteractions(billingClient);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<Person> person = ArgumentCaptor.forClass(Person.class);
        verify(personService).createPerson(person.capture());
        ArgumentCaptor<BillingTrialRequest> body = ArgumentCaptor.forClass(BillingTrialRequest.class);
        verify(billingClient).createTrial(eq(person.getValue().getTenantId()), body.capture());
        assertThat(body.getValue().billingEmail()).isEqualTo("empresa@test.com");
        assertThat(body.getValue().source()).isEqualTo("USER_MANAGER_REGISTER");
    }

    @Test
    void registerFailureNeverRequestsBillingTrial() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.createUserForRegister(any())).thenThrow(new IllegalStateException("db"));

        assertThatThrownBy(() -> service.register(request(null))).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(billingClient);
    }

    @Test
    void billingClientExceptionDoesNotBreakRegister() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingClient.createTrial(anyString(), any())).thenThrow(new IllegalStateException("billing caido"));
        UserResponseDto expected = new UserResponseDto();
        when(userService.createUserForRegister(any())).thenReturn(expected);

        assertThat(service.register(request(null))).isSameAs(expected);
        verify(billingClient).createTrial(anyString(), any());
    }
}
