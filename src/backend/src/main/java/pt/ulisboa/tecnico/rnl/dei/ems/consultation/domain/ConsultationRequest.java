package pt.ulisboa.tecnico.rnl.dei.ems.consultation.domain;

import java.time.Instant;

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
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;

/**
 * A student's request to consult (view the PDF and per-fragment grades of)
 * their corrected exam. One request per exam at a time.
 *
 * <ul>
 *   <li>{@code PENDING} — waiting for school staff to decide.</li>
 *   <li>{@code APPROVED} — staff granted access; the exam transitioned to
 *       {@link Exam.ExamStatus#RELEASED} and the student may now consult it.</li>
 *   <li>{@code REJECTED} — staff denied; the student may file a new request.</li>
 * </ul>
 */
@Data
@Entity
@Table(name = "consultation_requests")
public class ConsultationRequest {

	public enum Status {
		PENDING,
		APPROVED,
		REJECTED
	}

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "exam_id", nullable = false)
	private Exam exam;

	// Denormalized from exam.student so per-student queries don't need to join.
	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private Person student;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private Status status;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@ManyToOne
	@JoinColumn(name = "decided_by_id")
	private Person decidedBy;

	protected ConsultationRequest() {
	}

	public ConsultationRequest(Exam exam, Person student) {
		this.exam = exam;
		this.student = student;
		this.status = Status.PENDING;
		this.requestedAt = Instant.now();
	}
}