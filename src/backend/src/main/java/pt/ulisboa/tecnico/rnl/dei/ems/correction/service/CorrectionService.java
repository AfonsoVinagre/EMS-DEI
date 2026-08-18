package pt.ulisboa.tecnico.rnl.dei.ems.correction.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Status;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.CorrectionTaskDto;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.ReassignTaskRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.SubmitGradeRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.repository.CorrectionTaskRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.repository.ExamRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository.FragmentRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.person.repository.PersonRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.domain.RevisionItem;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.repository.RevisionItemRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@Service
@Transactional
public class CorrectionService {

	@Autowired
	private CorrectionTaskRepository correctionTaskRepository;

	@Autowired
	private FragmentRepository fragmentRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private RevisionItemRepository revisionItemRepository;

	/**
	 * Builds the {@link CorrectionTaskDto} for a task. For {@code REVISION}
	 * tasks, attaches the student's justification (read-only) fetched from the
	 * matching {@link RevisionItem} so the revising teacher can evaluate it.
	 * PRIMARY tasks get {@code null}.
	 */
	private CorrectionTaskDto toDto(CorrectionTask task) {
		String justification = null;
		if (task.getPhase() == CorrectionTask.Phase.REVISION) {
			justification = revisionItemRepository.findByFragmentId(task.getFragment().getId()).stream()
					.map(RevisionItem::getJustification)
					.findFirst()
					.orElse(null);
		}
		return new CorrectionTaskDto(task, justification);
	}

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
		return tasks.stream().map(this::toDto).toList();
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
		return toDto(task);
	}

	@Transactional
	public List<CorrectionTaskDto> listByFragment(long fragmentId) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		// 404 if fragment doesn't exist (cleaner error than empty list).
		if (!fragmentRepository.existsById(fragmentId)) {
			throw new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(fragmentId));
		}
		List<CorrectionTask> tasks = correctionTaskRepository.findByFragmentId(fragmentId);
		// Teachers only see their own tasks on this fragment; admins/staff
		// see the full history. This keeps the privacy rule that a teacher
		// must not be able to discover who else graded the same question.
		if (!canSeeAllTasks(current)) {
			tasks = tasks.stream()
					.filter(t -> t.getTeacher().getId().equals(current.getId()))
					.toList();
		}
		return tasks.stream().map(this::toDto).toList();
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

		// A fragment is "corrected" once its task is SUBMITTED. Every time a
		// fragment is corrected, check whether the whole exam is now fully
		// corrected — i.e. every fragment has at least one SUBMITTED task —
		// and if so advance the exam from DISTRIBUTED to CORRECTED.
		maybeMarkExamCorrected(task);
		return toDto(task);
	}

	/**
	 * Advances the exam of the just-submitted task to {@link Exam.ExamStatus#CORRECTED}
	 * when every one of its fragments has at least one SUBMITTED correction
	 * task. Only transitions from DISTRIBUTED — never regresses an exam that
	 * is already CORRECTED or RELEASED.
	 */
	private void maybeMarkExamCorrected(CorrectionTask justSubmitted) {
		Exam exam = examRepository.findById(justSubmitted.getFragment().getExam().getId())
				.orElse(null);
		if (exam == null || exam.getStatus() != Exam.ExamStatus.DISTRIBUTED) {
			return;
		}
		List<Fragment> fragments = fragmentRepository.findByExamId(exam.getId());
		if (fragments.isEmpty()) {
			return;
		}
		Set<Long> submittedFragmentIds = correctionTaskRepository
				.findByFragmentExamId(exam.getId()).stream()
				.filter(t -> t.getStatus() == Status.SUBMITTED)
				.map(t -> t.getFragment().getId())
				.collect(Collectors.toSet());
		boolean allCorrected = fragments.stream()
				.allMatch(f -> submittedFragmentIds.contains(f.getId()));
		if (allCorrected) {
			exam.setStatus(Exam.ExamStatus.CORRECTED);
			examRepository.save(exam);
		}
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
		return toDto(task);
	}
}
