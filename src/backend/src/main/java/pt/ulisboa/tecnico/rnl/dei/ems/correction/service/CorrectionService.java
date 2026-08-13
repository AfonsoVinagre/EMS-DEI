package pt.ulisboa.tecnico.rnl.dei.ems.correction.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Phase;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Status;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.CorrectionTaskDto;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.ReassignTaskRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.SubmitGradeRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.repository.CorrectionTaskRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository.FragmentRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.person.repository.PersonRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@Service
@Transactional
public class CorrectionService {

	@Autowired
	private CorrectionTaskRepository correctionTaskRepository;

	@Autowired
	private FragmentRepository fragmentRepository;

	@Autowired
	private PersonRepository personRepository;

	private boolean canSeeAllTasks(Person current) {
		return current != null && (current.getType() == PersonType.ADMINISTRATOR
				|| current.getType() == PersonType.SCHOOL_STAFF);
	}

	@Transactional
	public List<CorrectionTaskDto> listTasks() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		List<CorrectionTask> tasks = canSeeAllTasks(current)
				? correctionTaskRepository.findAll()
				: correctionTaskRepository.findByTeacherId(current.getId());
		return tasks.stream().map(CorrectionTaskDto::new).toList();
	}

	@Transactional
	public CorrectionTaskDto getTask(long id) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		CorrectionTask task = correctionTaskRepository.findById(id)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_CORRECTION_TASK, Long.toString(id)));
		if (!canSeeAllTasks(current) && !task.getTeacher().getId().equals(current.getId())) {
			throw new DEIException(ErrorMessage.NO_SUCH_CORRECTION_TASK, Long.toString(id));
		}
		return new CorrectionTaskDto(task);
	}

	@Transactional
	public List<CorrectionTaskDto> listByFragment(long fragmentId) {
		Person current = SecurityUtils.currentPerson();
		if (current == null || (!canSeeAllTasks(current))) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		// 404 if fragment doesn't exist (cleaner error than empty list).
		if (!fragmentRepository.existsById(fragmentId)) {
			throw new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(fragmentId));
		}
		return correctionTaskRepository.findByFragmentId(fragmentId).stream()
				.map(CorrectionTaskDto::new).toList();
	}

	/**
	 * Teacher submits a grade for their own task. Only the assigned teacher
	 * may do this; only on still-open tasks. Grade must be >= 0.
	 */
	@Transactional
	public CorrectionTaskDto submitGrade(long taskId, SubmitGradeRequest request) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		CorrectionTask task = correctionTaskRepository.findById(taskId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_CORRECTION_TASK, Long.toString(taskId)));

		if (!task.getTeacher().getId().equals(current.getId())) {
			throw new DEIException(ErrorMessage.NOT_THE_ASSIGNED_TEACHER);
		}
		if (task.getStatus() == Status.SUBMITTED) {
			throw new DEIException(ErrorMessage.TASK_NOT_OPEN);
		}
		Double grade = request.grade();
		if (grade == null || grade < 0) {
			throw new DEIException(ErrorMessage.GRADE_NOT_VALID);
		}
		task.setGrade(grade);
		task.setStatus(Status.SUBMITTED);
		task.setSubmittedAt(Instant.now());
		correctionTaskRepository.save(task);
		return new CorrectionTaskDto(task);
	}

	/**
	 * Admin moves a task to another teacher. Same phase — for a phase change
	 * the caller should use {@link #requestRevision(long, long)}.
	 */
	@Transactional
	public CorrectionTaskDto reassign(long taskId, ReassignTaskRequest request) {
		Person newTeacher = personRepository.findById(request.teacherId())
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(request.teacherId())));
		if (newTeacher.getType() != PersonType.TEACHER) {
			throw new DEIException(ErrorMessage.PERSON_NOT_TEACHER, Long.toString(newTeacher.getId()));
		}
		CorrectionTask task = correctionTaskRepository.findById(taskId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_CORRECTION_TASK, Long.toString(taskId)));

		// Revisor cannot be someone who already corrected this fragment.
		long fragmentId = task.getFragment().getId();
		List<CorrectionTask> existing = correctionTaskRepository.findByFragmentId(fragmentId);
		boolean alreadyCorrected = existing.stream()
				.anyMatch(t -> t.getTeacher().getId().equals(newTeacher.getId()) && t.getId() != taskId);
		if (alreadyCorrected) {
			throw new DEIException(ErrorMessage.TEACHER_CANNOT_REVISE_HIMSELF);
		}

		task.setTeacher(newTeacher);
		task.setStatus(Status.ASSIGNED);
		correctionTaskRepository.save(task);
		return new CorrectionTaskDto(task);
	}

	/**
	 * Admin opens a REVISION phase on a fragment with a teacher that hasn't
	 * corrected this fragment before. The original PRIMARY task stays — it
	 * is history.
	 */
	@Transactional
	public CorrectionTaskDto requestRevision(long fragmentId, long newTeacherId) {
		Fragment fragment = fragmentRepository.findById(fragmentId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(fragmentId)));
		Person newTeacher = personRepository.findById(newTeacherId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(newTeacherId)));
		if (newTeacher.getType() != PersonType.TEACHER) {
			throw new DEIException(ErrorMessage.PERSON_NOT_TEACHER, Long.toString(newTeacher.getId()));
		}

		List<CorrectionTask> existing = correctionTaskRepository.findByFragmentId(fragmentId);
		boolean alreadyCorrected = existing.stream()
				.anyMatch(t -> t.getTeacher().getId().equals(newTeacher.getId()));
		if (alreadyCorrected) {
			throw new DEIException(ErrorMessage.TEACHER_CANNOT_REVISE_HIMSELF);
		}

		CorrectionTask revision = correctionTaskRepository.save(
				new CorrectionTask(fragment, newTeacher, Phase.REVISION));
		return new CorrectionTaskDto(revision);
	}
}
