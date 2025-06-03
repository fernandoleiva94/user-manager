package com.sevenb.user_manager.service;

import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.exception.ResourceAlreadyExistsException;
import com.sevenb.user_manager.mapper.UserMapper;
import com.sevenb.user_manager.repository.PersonRepository;
import com.sevenb.user_manager.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;

    }


    public List<Person> getAllPeople() {
        return personRepository.findAll();
    }

    public Optional<Person> getPersonById(Long id) {
        return personRepository.findById(id);
    }

    public Person createPerson(Person person) {
        try{
            return personRepository.save(person);
        }catch(Exception e){
            throw new DataIntegrityViolationException("Datos incorrectos" , e);
        }
    }



    public Person updatePerson(Long id, Person updatedPerson) {
        return personRepository.findById(id).map(person -> {
            person.setFirstName(updatedPerson.getFirstName());
            person.setLastName(updatedPerson.getLastName());
            person.setDocumentType(updatedPerson.getDocumentType());
            person.setDocumentNumber(updatedPerson.getDocumentNumber());
            person.setEmail(updatedPerson.getEmail());
            person.setPhone(updatedPerson.getPhone());
            person.setAddress(updatedPerson.getAddress());
            person.setTaxCondition(updatedPerson.getTaxCondition());
            return personRepository.save(person);
        }).orElseThrow(() -> new RuntimeException("Person not found"));
    }

    public void deletePerson(Long id) {
        personRepository.deleteById(id);
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
