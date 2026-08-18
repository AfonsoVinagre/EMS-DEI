package pt.ulisboa.tecnico.rnl.dei.ems.revision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;

/**
 * One targeted question of a {@link RevisionRequest}: a fragment plus the
 * student's justification for revising it. The justification lives here, on
 * the request side — never on the correction task (the task stays free of a
 * justification field, per the product decision).
 */
@Data
@Entity
@Table(name = "revision_items")
public class RevisionItem {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "revision_request_id", nullable = false)
	private RevisionRequest revisionRequest;

	@ManyToOne(optional = false)
	@JoinColumn(name = "fragment_id", nullable = false)
	private Fragment fragment;

	@Column(name = "justification", nullable = false, length = 2000)
	private String justification;

	protected RevisionItem() {
	}

	public RevisionItem(Fragment fragment, String justification) {
		this.fragment = fragment;
		this.justification = justification;
	}
}