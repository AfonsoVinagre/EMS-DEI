package pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;

/**
 * A physical fragment of an exam — one per page of the uploaded PDF.
 *
 * A Fragment is intentionally minimal: it only knows how it was physically
 * carved out of the source exam. The correction workflow (who grades it,
 * what grade they gave, whether it needs revision) lives on
 * {@link pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask}.
 *
 * Therefore: no assignedTeacher, no grade, no persisted status, no
 * justification. Status is {@link #status}, computed on the fly.
 */
@Data
@Entity
@Table(name = "fragments")
public class Fragment {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "exam_id", nullable = false)
	private Exam exam;

	@Column(name = "page_number", nullable = false)
	private int pageNumber;

	@Column(name = "image_path", nullable = false)
	private String imagePath;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	// Computed by the service layer on every read — never persisted.
	@Transient
	private String status;

	// The grade of the fragment's corrected (SUBMITTED) PRIMARY task, or null
	// while it is not yet corrected. Computed by the service — never persisted.
	@Transient
	private Double grade;

	protected Fragment() {
	}

	public Fragment(Exam exam, int pageNumber, String imagePath, Subject subject) {
		this.exam = exam;
		this.pageNumber = pageNumber;
		this.imagePath = imagePath;
		this.subject = subject;
	}
}
