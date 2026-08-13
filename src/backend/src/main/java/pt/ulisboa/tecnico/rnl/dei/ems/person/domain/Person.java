package pt.ulisboa.tecnico.rnl.dei.ems.person.domain;


import jakarta.persistence.*;

import lombok.Data;
import lombok.ToString;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;

// Domain class representing a person in the system.
// A person is also a login account: it has an email + password, and its
// PersonType is the security role that determines what it is allowed to do.
@Data
@Entity
@Table(name = "people")
public class Person {

	public enum PersonType {
		ADMINISTRATOR,
		SCHOOL_STAFF,
		TEACHER,
		STUDENT
	}

	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	// Login identifier, unique across all accounts.
	@Column(name = "email", nullable = false, unique = true)
	private String email;

	// BCrypt hash of the password; excluded from toString so it is never logged.
	@ToString.Exclude
	@Column(name = "password", nullable = false)
	private String password;

	@Column(name = "type", nullable = false)
	@Enumerated(EnumType.STRING)
    private PersonType type;

	// The school this person belongs to. NULL for ADMINISTRATOR (admins
	// operate across all schools). Mandatory in practice for SCHOOL_STAFF,
	// TEACHER and STUDENT — enforced at the service layer.
	@ManyToOne
	@JoinColumn(name = "school_id")
	private School school;

	protected Person() {
	}

	public Person(String name, String email, String password, PersonType type) {
		this.name = name;
		this.email = email;
		this.password = password;
		this.type = type;
	}

	public Person(String name, String email, String password, PersonType type, School school) {
		this.name = name;
		this.email = email;
		this.password = password;
		this.type = type;
		this.school = school;
	}
}
