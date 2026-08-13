package pt.ulisboa.tecnico.rnl.dei.ems.correction.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;

/**
 * A single correction attempt on a {@link Fragment}. One fragment can have
 * many tasks (history of corrections / revisions), but at most one per
 * {@link Phase} value, enforced by the unique constraint.
 *
 * No {@code justification} field: this was explicitly rejected by product.
 */
@Data
@Entity
@Table(name = "correction_tasks", uniqueConstraints = @UniqueConstraint(
		name = "uk_correction_task_fragment_phase",
		columnNames = { "fragment_id", "phase" }))
public class CorrectionTask {

	public enum Phase {
		PRIMARY,
		REVISION
	}

	public enum Status {
		ASSIGNED,
		IN_PROGRESS,
		SUBMITTED
	}

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "fragment_id", nullable = false)
	private Fragment fragment;

	@ManyToOne(optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Person teacher;

	@Column(name = "phase", nullable = false)
	@Enumerated(EnumType.STRING)
	private Phase phase;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private Status status;

	@Column(name = "grade")
	private Double grade;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	protected CorrectionTask() {
	}

	public CorrectionTask(Fragment fragment, Person teacher, Phase phase) {
		this.fragment = fragment;
		this.teacher = teacher;
		this.phase = phase;
		this.status = Status.ASSIGNED;
		this.assignedAt = Instant.now();
	}
}
