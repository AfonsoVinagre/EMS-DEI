package pt.ulisboa.tecnico.rnl.dei.ems.exam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;

@Data
@Entity
@Table(name = "exams")
public class Exam {

	public enum ExamStatus {
		UPLOADED,
		DISTRIBUTED,
		CORRECTED,
		RELEASED
	}

	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "pdf_path", nullable = false)
	private String pdfPath;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private Person student;

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_id", nullable = false)
	private School school;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@ManyToOne(optional = false)
	@JoinColumn(name = "uploaded_by_id", nullable = false)
	private Person uploadedBy;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private ExamStatus status;

	protected Exam() {
	}

	public Exam(String pdfPath, String originalFilename, Person student, School school,
			Subject subject, Person uploadedBy) {
		this.pdfPath = pdfPath;
		this.originalFilename = originalFilename;
		this.student = student;
		this.school = school;
		this.subject = subject;
		this.uploadedBy = uploadedBy;
		this.status = ExamStatus.UPLOADED;
	}
}