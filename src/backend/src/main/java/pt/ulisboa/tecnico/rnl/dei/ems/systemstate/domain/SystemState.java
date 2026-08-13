package pt.ulisboa.tecnico.rnl.dei.ems.systemstate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Singleton row (id always 1) that stores application-wide state which is not
 * tied to any specific entity. Currently tracks whether new exam uploads by
 * SCHOOL_STAFF are globally locked.
 *
 * The single-row pattern is intentional — this is configuration-style state,
 * not a multi-instance collection. Seeded on application start by
 * {@link pt.ulisboa.tecnico.rnl.dei.ems.config.DataInitializer}.
 */
@Data
@Entity
@Table(name = "system_state")
public class SystemState {

	@Id
	@Column(name = "id")
	private Long id = 1L;

	@Column(name = "exams_locked", nullable = false)
	private boolean examsLocked = false;

	protected SystemState() {
	}

	public SystemState(boolean examsLocked) {
		this.id = 1L;
		this.examsLocked = examsLocked;
	}
}
