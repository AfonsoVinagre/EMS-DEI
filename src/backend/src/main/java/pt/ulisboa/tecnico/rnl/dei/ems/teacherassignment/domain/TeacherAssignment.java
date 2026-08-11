package pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;

@Data
@Entity
@Table(name = "teacher_assignments", uniqueConstraints = @UniqueConstraint(
		name = "uk_teacher_assignment",
		columnNames = { "teacher_id", "school_id", "subject_id" }))
public class TeacherAssignment {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Person teacher;

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_id", nullable = false)
	private School school;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	protected TeacherAssignment() {
	}

	public TeacherAssignment(Person teacher, School school, Subject subject) {
		this.teacher = teacher;
		this.school = school;
		this.subject = subject;
	}
}
