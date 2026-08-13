package pt.ulisboa.tecnico.rnl.dei.ems.person;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.dto.PersonDto;
import pt.ulisboa.tecnico.rnl.dei.ems.person.service.PersonService;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@RestController
public class PersonController {
	@Autowired
	private PersonService personService;

	@GetMapping("/people")
	@PreAuthorize("hasAuthority('PERSON_READ')")
	public List<PersonDto> getPeople() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return personService.getPeople(current);
	}

	@PostMapping("/people")
	@PreAuthorize("hasAuthority('PERSON_CREATE')")
	public PersonDto createPerson(@RequestBody PersonDto personDto) {
		return personService.createPerson(personDto);
	}

	@GetMapping("/people/{id}")
	@PreAuthorize("hasAuthority('PERSON_READ')")
	public PersonDto getPerson(@PathVariable long id) {
		return personService.getPerson(id);
	}

	@PutMapping("/people/{id}")
	@PreAuthorize("hasAuthority('PERSON_UPDATE')")
	public PersonDto updatePerson(@PathVariable long id, @RequestBody PersonDto personDto) {
		return personService.updatePerson(id, personDto);
	}

	@DeleteMapping("/people/{id}")
	@PreAuthorize("hasAuthority('PERSON_DELETE')")
	public void deletePerson(@PathVariable long id) {
		personService.deletePerson(id);
	}
}
