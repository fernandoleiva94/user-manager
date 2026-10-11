package com.sevenb.user_manager.service;

import com.sevenb.user_manager.client.BillingTrialClient;
import com.sevenb.user_manager.client.BillingTrialRequest;
import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegisterServiceTest {

    private final PersonService personService = mock(PersonService.class);
    private final UserService userService = mock(UserService.class);
    private final RoleService roleService = mock(RoleService.class);
    private final BillingTrialClient billingClient = mock(BillingTrialClient.class);
    private final ManualScheduler scheduler = new ManualScheduler();
    private final BillingTrialService billingTrialService = new BillingTrialService(
            billingClient, scheduler.executor, List.of(Duration.ofSeconds(2)), 100, Duration.ZERO);
    private final RegisterService service =
            new RegisterService(personService, userService, roleService, billingTrialService);

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

    /** RegisterService detras del mismo interceptor transaccional que arma Spring, con un TM falso. */
    private RegisterService transactional(PlatformTransactionManager tm) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(tm, new AnnotationTransactionAttributeSource()));
        return (RegisterService) factory.getProxy();
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
    void registerIsTransactionalAndRollsBackOnRuntimeException() throws NoSuchMethodException {
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource().getTransactionAttribute(
                RegisterService.class.getMethod("register", RegisterDTO.class), RegisterService.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.rollbackOn(new RuntimeException())).isTrue();
    }

    @Test
    void committedRegisterCallsBillingOnceAfterCommit() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingClient.createTrial(anyString(), any())).thenReturn(BillingTrialClient.Result.CREATED);
        FakeTransactionManager tm = new FakeTransactionManager(false);

        transactional(tm).register(request(null));
        scheduler.runAll();

        assertThat(tm.commits).isEqualTo(1);
        ArgumentCaptor<Person> person = ArgumentCaptor.forClass(Person.class);
        verify(personService).createPerson(person.capture());
        ArgumentCaptor<BillingTrialRequest> body = ArgumentCaptor.forClass(BillingTrialRequest.class);
        verify(billingClient, times(1)).createTrial(eq(person.getValue().getTenantId()), body.capture());
        assertThat(body.getValue().billingEmail()).isEqualTo("empresa@test.com");
        assertThat(body.getValue().source()).isEqualTo("USER_MANAGER_REGISTER");
    }

    @Test
    void failedCommitNeverCallsBilling() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        FakeTransactionManager tm = new FakeTransactionManager(true);

        assertThatThrownBy(() -> transactional(tm).register(request(null)))
                .isInstanceOf(TransactionSystemException.class);
        scheduler.runAll();

        verifyNoInteractions(billingClient);
        verifyNoInteractions(scheduler.executor);
    }

    @Test
    void registerExceptionRollsBackAndNeverCallsBilling() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.createUserForRegister(any())).thenThrow(new IllegalStateException("db"));
        FakeTransactionManager tm = new FakeTransactionManager(false);

        assertThatThrownBy(() -> transactional(tm).register(request(null))).isInstanceOf(IllegalStateException.class);

        assertThat(tm.rollbacks).isEqualTo(1);
        verifyNoInteractions(billingClient);
    }

    @Test
    void billingClientExceptionDoesNotBreakRegister() {
        when(personService.createPerson(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingClient.createTrial(anyString(), any())).thenThrow(new IllegalStateException("billing caido"));
        UserResponseDto expected = new UserResponseDto();
        when(userService.createUserForRegister(any())).thenReturn(expected);

        assertThat(transactional(new FakeTransactionManager(false)).register(request(null))).isSameAs(expected);
        scheduler.runAll();

        verify(billingClient).createTrial(anyString(), any());
    }
}
