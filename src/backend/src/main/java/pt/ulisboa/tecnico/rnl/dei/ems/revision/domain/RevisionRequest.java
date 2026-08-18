package pt.ulisboa.tecnico.rnl.dei.ems.revision.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;

/**
 * A student's revision request for one of their released exams (Fase 5). One
 * request per (student, exam) per revision window; the window opens
 * automatically when the exam is released and closes {@code windowHours}
 * later. Each request targets one or more specific fragments (questions),
 * each with its own justification, via {@link RevisionItem}.
 *
 * <p>The request itself carries no grade or decision field: the "decision" is
 * the {@code REVISION} {@link pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask}
 * created for each targeted fragment when the window closes, whose submitted
 * grade becomes the definitive grade of that fragment.
 */
@Data
@Entity
@Table(name = "revision_requests")
public class RevisionRequest {

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

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@OneToMany(mappedBy = "revisionRequest", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<RevisionItem> items = new ArrayList<>();

	protected RevisionRequest() {
	}

	public RevisionRequest(Exam exam, Person student) {
		this.exam = exam;
		this.student = student;
		this.requestedAt = Instant.now();
	}

	public void addItem(RevisionItem item) {
		item.setRevisionRequest(this);
		items.add(item);
	}
}