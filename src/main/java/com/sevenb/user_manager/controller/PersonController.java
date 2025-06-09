package com.sevenb.user_manager.controller;


import com.sevenb.user_manager.dto.PersonResponseDto;
import com.sevenb.user_manager.dto.RegisterDTO;
import com.sevenb.user_manager.dto.UserResponseDto;
import com.sevenb.user_manager.entity.Person;
import com.sevenb.user_manager.service.PersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;

    }

    @GetMapping
    public List<Person> getAllPeople() {
        return personService.getAllPeople();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonResponseDto> getPersonById(@PathVariable Long id) {
        return ResponseEntity.ok(personService.getPersonById(id));
    }

    @PostMapping
    public Person createPerson(@RequestBody Person person) {
        return personService.createPerson(person);
    }




    @PutMapping("/{id}")
    public ResponseEntity<PersonResponseDto> updatePerson(@PathVariable Long id, @RequestBody Person person) {
        try {
            PersonResponseDto updatedPerson = personService.updatePerson(id, person);
            return ResponseEntity.ok(updatedPerson);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable Long id) {
        personService.deletePerson(id);
        return ResponseEntity.noContent().build();
    }
}
