package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.PersonResponseDto;
import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.exception.ResourceAlreadyExistsException;
import com.sevenb.user_manager.mapper.UserMapper;
import com.sevenb.user_manager.repository.PersonRepository;
import com.sevenb.user_manager.repository.UserRepository;
import com.sevenb.user_manager.security.PermissionGuard;
import com.sevenb.user_manager.security.TenantContext;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final PermissionGuard permissionGuard;

    public PersonService(PersonRepository personRepository, PermissionGuard permissionGuard) {
        this.personRepository = personRepository;
        this.permissionGuard = permissionGuard;
    }


    /** Platform admin: todas las empresas. Resto: solo la empresa de su tenant (0 o 1). */
    public List<Person> getAllPeople() {
        if (permissionGuard.isPlatformAdmin()) {
            return personRepository.findAll();
        }
        return personRepository.findByTenantId(TenantContext.requireTenantId())
                .map(List::of)
                .orElseGet(List::of);
    }

    public PersonResponseDto getPersonById(Long id) {
        return toPersonDto(getAccessiblePerson(id));
    }

    /**
     * Platform admin accede a cualquier empresa; el resto solo a la de su tenant.
     * Fuera de alcance responde "no encontrada" (404) para no revelar que existe.
     */
    private Person getAccessiblePerson(Long id) {
        Optional<Person> person = permissionGuard.isPlatformAdmin()
                ? personRepository.findById(id)
                : personRepository.findByIdAndTenantId(id, TenantContext.requireTenantId());
        return person.orElseThrow(() -> new RuntimeException("Persona no encontrada: " + id));
    }

    private PersonResponseDto toPersonDto ( Person person){
        PersonResponseDto personResponseDto = new PersonResponseDto();
        personResponseDto.setId(person.getId());
        personResponseDto.setEmail(person.getEmail());
        personResponseDto.setAddress(person.getAddress());
        personResponseDto.setPhone(person.getPhone());
        personResponseDto.setDocumentType(person.getDocumentType());
        personResponseDto.setDocumentNumber(person.getDocumentNumber());
        personResponseDto.setFirstName(person.getFirstName());
        personResponseDto.setLastName(person.getLastName());
        personResponseDto.setTaxCondition(person.getTaxCondition());
        personResponseDto.setTenantId(person.getTenantId());

        return personResponseDto;

    }

    public Person createPerson(Person person) {
        try{
            return personRepository.save(person);
        }catch(Exception e){
            throw new DataIntegrityViolationException("Datos incorrectos" , e);
        }
    }

    /** Alta directa de empresa: solo platform admin. El tenantId lo genera el servidor. */
    public Person createPersonAsPlatformAdmin(Person person) {
        if (!permissionGuard.isPlatformAdmin()) {
            throw new AccessDeniedException("Solo un administrador de la plataforma puede crear empresas");
        }
        person.setId(null);
        person.setTenantId(UUID.randomUUID().toString());
        return createPerson(person);
    }



    public PersonResponseDto updatePerson(Long id, Person updatedPerson) {
        Person person = getAccessiblePerson(id);
        person.setFirstName(updatedPerson.getFirstName());
        person.setLastName(updatedPerson.getLastName());
        person.setDocumentType(updatedPerson.getDocumentType());
        person.setDocumentNumber(updatedPerson.getDocumentNumber());
        person.setEmail(updatedPerson.getEmail());
        person.setPhone(updatedPerson.getPhone());
        person.setAddress(updatedPerson.getAddress());
        person.setTaxCondition(updatedPerson.getTaxCondition());
        return toPersonDto(personRepository.save(person));
    }

    public void deletePerson(Long id) {
        personRepository.delete(getAccessiblePerson(id));
    }

    public void mailValiation(String email){
        if (personRepository.existsByEmail(email))
            throw new ResourceAlreadyExistsException("Cuenta existente con el email : " + email);
    }
    public void documentNumberValidate(String documentNumber){
        if (personRepository.existsByDocumentNumber(documentNumber))
            throw new ResourceAlreadyExistsException("Cuenta existente con el numero de documento : " + documentNumber);
    }



}
