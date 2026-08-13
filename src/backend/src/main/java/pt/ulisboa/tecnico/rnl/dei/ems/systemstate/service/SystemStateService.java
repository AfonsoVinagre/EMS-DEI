package pt.ulisboa.tecnico.rnl.dei.ems.systemstate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.repository.ExamRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.domain.SystemState;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.dto.SystemStateDto;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.repository.SystemStateRepository;

/**
 * Owns the global lock on new exam uploads.
 *
 * Lock is only allowed when there are no exams in UPLOADED state — staff are
 * responsible for segmenting everything that was uploaded before the admin
 * closes the upload window.
 */
@Service
@Transactional
public class SystemStateService {

	@Autowired
	private SystemStateRepository systemStateRepository;

	@Autowired
	private ExamRepository examRepository;

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
		if (!examRepository.findByStatus(
				pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam.ExamStatus.UPLOADED).isEmpty()) {
			throw new DEIException(ErrorMessage.EXAMS_NOT_ALL_SEGMENTED);
		}
		SystemState state = ensureRow();
		state.setExamsLocked(true);
		systemStateRepository.save(state);
		return new SystemStateDto(state);
	}

	@Transactional
	public SystemStateDto unlockUploads() {
		SystemState state = ensureRow();
		state.setExamsLocked(false);
		systemStateRepository.save(state);
		return new SystemStateDto(state);
	}

	public void assertStaffCanUpload() {
		if (ensureRow().isExamsLocked()) {
			throw new DEIException(ErrorMessage.EXAM_UPLOADS_GLOBALLY_LOCKED);
		}
	}
}
