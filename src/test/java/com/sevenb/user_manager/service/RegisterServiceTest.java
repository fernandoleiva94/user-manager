package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.entity.RoleEntity;
import com.sevenb.user_manager.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterServiceTest {

    private final PersonService personService = mock(PersonService.class);
    private final UserService userService = mock(UserService.class);
    private final RoleService roleService = mock(RoleService.class);
    private final RegisterService service = new RegisterService(personService, userService, roleService);

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
}
