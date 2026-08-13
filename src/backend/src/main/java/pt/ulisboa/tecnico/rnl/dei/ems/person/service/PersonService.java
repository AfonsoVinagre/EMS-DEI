package pt.ulisboa.tecnico.rnl.dei.ems.person.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.person.dto.PersonDto;
import pt.ulisboa.tecnico.rnl.dei.ems.person.repository.PersonRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.school.repository.SchoolRepository;

// Service class for managing Person entities
@Service
@Transactional
public class PersonService {

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SchoolRepository schoolRepository;

	private Person fetchPersonOrThrow(long id) {
		return personRepository.findById(id)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(id)));
	}

	private School fetchSchoolOrNull(Long schoolId) {
		if (schoolId == null) {
			return null;
		}
		return schoolRepository.findById(schoolId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SCHOOL, Long.toString(schoolId)));
	}

	/**
	 * Scoped listing. Visibility rules:
	 *
	 * <ul>
	 *   <li>ADMINISTRATOR: every person.</li>
	 *   <li>SCHOOL_STAFF: only people of their own school (so they can pick
	 *       their own students/colleagues); fails with 5026 if the staff has
	 *       no school assigned.</li>
	 *   <li>TEACHER, STUDENT: only themselves (privacy: teachers must not
	 *       see who they are correcting; students only need to see their
	 *       own profile).</li>
	 * </ul>
	 *
	 * Cosmetic filtering on the frontend is not a substitute for this — the
	 * API itself must not leak people outside the caller's scope.
	 */
	@Transactional
	public List<PersonDto> getPeople(Person current) {
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		if (current.getType() == PersonType.ADMINISTRATOR) {
			return personRepository.findAll().stream().map(PersonDto::new).toList();
		}
		if (current.getType() == PersonType.SCHOOL_STAFF) {
			if (current.getSchool() == null) {
				throw new DEIException(ErrorMessage.PERSON_SCHOOL_REQUIRED);
			}
			return personRepository.findAll().stream()
					.filter(p -> p.getSchool() != null
							&& p.getSchool().getId().equals(current.getSchool().getId()))
					.map(PersonDto::new)
					.toList();
		}
		// TEACHER, STUDENT: themselves only.
		return List.of(new PersonDto(current));
	}

	private PersonDto persist(PersonDto personDto) {
		PersonType type = PersonType.valueOf(personDto.type().toUpperCase());

		if (type == PersonType.ADMINISTRATOR && personDto.schoolId() != null) {
			throw new DEIException(ErrorMessage.PERSON_SCHOOL_NOT_APPLICABLE);
		}
		if (type != PersonType.ADMINISTRATOR && personDto.schoolId() == null) {
			throw new DEIException(ErrorMessage.PERSON_SCHOOL_REQUIRED);
		}

		School school = fetchSchoolOrNull(personDto.schoolId());
		Person person = new Person(
				personDto.name(),
				personDto.email(),
				passwordEncoder.encode(personDto.password()),
				type,
				school);
		return new PersonDto(personRepository.save(person));
	}

	@Transactional
	public PersonDto createPerson(PersonDto personDto) {
		return persist(personDto);
	}

	@Transactional
	public PersonDto getPerson(long id) {
		return new PersonDto(fetchPersonOrThrow(id));
	}

	@Transactional
	public PersonDto updatePerson(long id, PersonDto personDto) {
		Person person = fetchPersonOrThrow(id);

		PersonType type = PersonType.valueOf(personDto.type().toUpperCase());
		if (type == PersonType.ADMINISTRATOR && personDto.schoolId() != null) {
			throw new DEIException(ErrorMessage.PERSON_SCHOOL_NOT_APPLICABLE);
		}
		if (type != PersonType.ADMINISTRATOR && personDto.schoolId() == null) {
			throw new DEIException(ErrorMessage.PERSON_SCHOOL_REQUIRED);
		}

		person.setName(personDto.name());
		person.setType(type);
		person.setEmail(personDto.email());
		person.setPassword(passwordEncoder.encode(personDto.password()));
		person.setSchool(fetchSchoolOrNull(personDto.schoolId()));

		return new PersonDto(personRepository.save(person));
	}

	@Transactional
	public void deletePerson(long id) {
		fetchPersonOrThrow(id); // ensure exists

		personRepository.deleteById(id);
	}
}
