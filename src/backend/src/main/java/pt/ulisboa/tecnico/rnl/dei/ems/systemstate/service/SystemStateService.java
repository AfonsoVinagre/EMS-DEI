package pt.ulisboa.tecnico.rnl.dei.ems.systemstate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.domain.SystemState;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.dto.SystemStateDto;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.repository.SystemStateRepository;

/**
 * Owns the global lock on new exam uploads.
 *
 * Since uploads segment inline (segmentInternal is invoked from
 * uploadExam itself), no exam ever lingers in UPLOADED state — there is
 * no pending-work guard to enforce before locking.
 */
@Service
@Transactional
public class SystemStateService {

	@Autowired
	private SystemStateRepository systemStateRepository;

	private SystemState ensureRow() {
		return systemStateRepository.findById(1L)
				.orElseGet(() -> systemStateRepository.save(new SystemState(false)));
	}

	@Transactional
	public SystemStateDto get() {
		return new SystemStateDto(ensureRow());
	}

	@Transactional
	public SystemStateDto lockUploads() {
		SystemState state = ensureRow();
		state.setExamsLocked(true);
		systemStateRepository.save(state);
		return new SystemStateDto(state);
	}

	@Transactional
	public SystemStateDto unlockUploads() {
		SystemState state = ensureRow();
		// Once exams have been distributed, the lock is permanent. The admin
		// is expected to commit to the distribution cycle at that point.
		if (state.isDistributed()) {
			throw new DEIException(ErrorMessage.EXAMS_ALREADY_DISTRIBUTED);
		}
		state.setExamsLocked(false);
		systemStateRepository.save(state);
		return new SystemStateDto(state);
	}

	public void assertStaffCanUpload() {
		if (ensureRow().isExamsLocked()) {
			throw new DEIException(ErrorMessage.EXAM_UPLOADS_GLOBALLY_LOCKED);
		}
	}

	/**
	 * Read-only check used by services that depend on the lock being set
	 * (e.g. exam distribution).
	 */
	public boolean isLocked() {
		return ensureRow().isExamsLocked();
	}

	/**
	 * Marks the singleton as having been distributed. Called from the exam
	 * distribution batch — once true, unlock is permanently refused.
	 */
	public void markDistributed() {
		SystemState state = ensureRow();
		state.setDistributed(true);
		systemStateRepository.save(state);
	}
}
