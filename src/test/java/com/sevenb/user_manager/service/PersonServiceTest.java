package com.sevenb.user_manager.service;

import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.repository.PersonRepository;
import com.sevenb.user_manager.security.PermissionGuard;
import com.sevenb.user_manager.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonServiceTest {

    private static final String TENANT = "tenant-1";

    private final PersonRepository personRepository = mock(PersonRepository.class);
    private final PermissionGuard permissionGuard = mock(PermissionGuard.class);
    private final PersonService service = new PersonService(personRepository, permissionGuard);

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT, 1L, 1L, "juan", "ADMIN", "RI");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Person person(long id, String tenantId) {
        Person person = new Person();
        person.setId(id);
        person.setTenantId(tenantId);
        person.setFirstName("Empresa " + id);
        return person;
    }

    private void platformAdmin(boolean value) {
        when(permissionGuard.isPlatformAdmin()).thenReturn(value);
    }

    @Test
    void platformAdminListsAllPeople() {
        platformAdmin(true);
        when(personRepository.findAll()).thenReturn(List.of(person(1L, TENANT), person(2L, "tenant-2")));

        assertThat(service.getAllPeople()).hasSize(2);
    }

    @Test
    void tenantUserListsOnlyOwnCompany() {
        platformAdmin(false);
        when(personRepository.findByTenantId(TENANT)).thenReturn(Optional.of(person(1L, TENANT)));

        assertThat(service.getAllPeople()).extracting(Person::getTenantId).containsExactly(TENANT);
        verify(personRepository, never()).findAll();
    }

    @Test
    void tenantUserWithoutCompanyGetsEmptyList() {
        platformAdmin(false);
        when(personRepository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThat(service.getAllPeople()).isEmpty();
    }

    @Test
    void tenantUserCanGetOwnCompany() {
        platformAdmin(false);
        when(personRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(person(1L, TENANT)));

        assertThat(service.getPersonById(1L).getTenantId()).isEqualTo(TENANT);
    }

    @Test
    void tenantUserCannotGetOtherCompany() {
        platformAdmin(false);
        when(personRepository.findByIdAndTenantId(2L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPersonById(2L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no encontrada");
        verify(personRepository, never()).findById(any());
    }

    @Test
    void platformAdminCanGetAnyCompany() {
        platformAdmin(true);
        when(personRepository.findById(2L)).thenReturn(Optional.of(person(2L, "tenant-2")));

        assertThat(service.getPersonById(2L).getTenantId()).isEqualTo("tenant-2");
    }

    @Test
    void updateKeepsTenantIdIgnoringBody() {
        platformAdmin(false);
        Person stored = person(1L, TENANT);
        when(personRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(stored));
        when(personRepository.save(stored)).thenReturn(stored);
        Person body = person(1L, "tenant-ajeno");
        body.setFirstName("Nuevo nombre");

        var result = service.updatePerson(1L, body);

        assertThat(result.getTenantId()).isEqualTo(TENANT);
        assertThat(result.getFirstName()).isEqualTo("Nuevo nombre");
        assertThat(stored.getTenantId()).isEqualTo(TENANT);
    }

    @Test
    void platformAdminUpdateAlsoKeepsTenantId() {
        platformAdmin(true);
        Person stored = person(2L, "tenant-2");
        when(personRepository.findById(2L)).thenReturn(Optional.of(stored));
        when(personRepository.save(stored)).thenReturn(stored);

        var result = service.updatePerson(2L, person(2L, "tenant-ajeno"));

        assertThat(result.getTenantId()).isEqualTo("tenant-2");
    }

    @Test
    void tenantUserCannotUpdateOtherCompany() {
        platformAdmin(false);
        when(personRepository.findByIdAndTenantId(2L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePerson(2L, person(2L, TENANT)))
                .isInstanceOf(RuntimeException.class);
        verify(personRepository, never()).save(any());
    }

    @Test
    void tenantUserCannotDeleteOtherCompany() {
        platformAdmin(false);

        assertThatThrownBy(() -> service.deletePerson(2L)).isInstanceOf(AccessDeniedException.class);
        verify(personRepository, never()).delete(any());
        verify(personRepository, never()).deleteById(any());
    }

    @Test
    void tenantUserCannotDeleteOwnCompany() {
        platformAdmin(false);
        when(personRepository.findByIdAndTenantId(1L, TENANT)).thenReturn(Optional.of(person(1L, TENANT)));
        when(personRepository.findById(1L)).thenReturn(Optional.of(person(1L, TENANT)));

        assertThatThrownBy(() -> service.deletePerson(1L)).isInstanceOf(AccessDeniedException.class);
        verify(personRepository, never()).delete(any());
        verify(personRepository, never()).deleteById(any());
    }

    @Test
    void platformAdminDeleteOfMissingCompanyIsNotFound() {
        platformAdmin(true);
        when(personRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePerson(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no encontrada");
        verify(personRepository, never()).delete(any());
    }

    @Test
    void platformAdminCanDeleteAnyCompany() {
        platformAdmin(true);
        Person other = person(2L, "tenant-2");
        when(personRepository.findById(2L)).thenReturn(Optional.of(other));

        service.deletePerson(2L);

        verify(personRepository).delete(other);
    }

    @Test
    void nonPlatformAdminCannotCreatePerson() {
        platformAdmin(false);

        assertThatThrownBy(() -> service.createPersonAsPlatformAdmin(person(5L, TENANT)))
                .isInstanceOf(AccessDeniedException.class);
        verify(personRepository, never()).save(any());
    }

    @Test
    void platformAdminCreatePersonGeneratesTenantId() {
        platformAdmin(true);
        when(personRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Person created = service.createPersonAsPlatformAdmin(person(5L, "tenant-del-body"));

        assertThat(created.getId()).isNull();
        assertThat(created.getTenantId()).isNotEqualTo("tenant-del-body");
        assertThat(UUID.fromString(created.getTenantId()).toString()).isEqualTo(created.getTenantId());
    }
}
