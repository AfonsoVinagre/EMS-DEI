package pt.ulisboa.tecnico.rnl.dei.ems.correction.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Status;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.CorrectionTaskDto;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.ReassignTaskRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.repository.CorrectionTaskRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.domain.AuditAction;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.service.AuditService;
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

	@Autowired
	private AuditService auditService;

	@Value("${ems.storage.annotations-dir:./data/annotations}")
	private String annotationsDir;

	private void ensureAnnotationsDirExists() {
		try {
			Files.createDirectories(Paths.get(annotationsDir));
		} catch (IOException e) {
			throw new DEIException(ErrorMessage.FILE_STORAGE_ERROR);
		}
	}

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
	 * may do this; only on still-open tasks. Grade must be >= 0. The optional
	 * {@code annotation} is the composited PNG (fragment image + the teacher's
	 * visual annotations) produced by the canvas tool — saved to disk and
	 * exposed to the student in consultation (the definitive task's annotation).
	 */
	@Transactional
	public CorrectionTaskDto submitGrade(long taskId, Double grade, MultipartFile annotation) {
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
		if (grade == null || grade < 0) {
			throw new DEIException(ErrorMessage.GRADE_NOT_VALID);
		}
		// Fragment's definitive grade before this submission (latest SUBMITTED
		// task on the same fragment, excluding this one) — the "previous value"
		// for the audit, so a revision surfaces as "alterou de 1.5 para 2.0".
		long fragmentId = task.getFragment().getId();
		Double prevDefinitive = correctionTaskRepository.findByFragmentId(fragmentId).stream()
				.filter(t -> t.getStatus() == Status.SUBMITTED
						&& (task.getId() == null || !t.getId().equals(task.getId())))
				.max(java.util.Comparator.comparing(
						CorrectionTask::getSubmittedAt,
						java.util.Comparator.nullsFirst(Instant::compareTo)))
				.map(CorrectionTask::getGrade)
				.orElse(null);

		// Persist the annotated PNG (if any) before flipping the task to
		// SUBMITTED, so the annotation is tied atomically to this grade.
		boolean hasAnnotation = annotation != null && !annotation.isEmpty();
		if (hasAnnotation) {
			ensureAnnotationsDirExists();
			Path target = Paths.get(annotationsDir).resolve(taskId + ".png");
			try {
				Files.copy(annotation.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new DEIException(ErrorMessage.FILE_STORAGE_ERROR);
			}
			task.setAnnotatedImagePath(target.toString());
		}

		task.setGrade(grade);
		task.setStatus(Status.SUBMITTED);
		task.setSubmittedAt(Instant.now());
		correctionTaskRepository.save(task);

		// Audit: the enunciado's headline example — "Professor X atribuiu a
		// nota Y ao fragmento Z" (with the previous grade when it's a change).
		int page = task.getFragment().getPageNumber();
		StringBuilder desc = new StringBuilder()
				.append("Professor ").append(current.getName())
				.append(" atribuiu a nota ").append(grade)
				.append(" ao fragmento ").append(fragmentId)
				.append(" (pergunta ").append(page).append(")");
		if (hasAnnotation) {
			desc.append(" com anotação visual");
		}
		if (prevDefinitive != null) {
			desc.append("; anterior: ").append(prevDefinitive);
		}
		auditService.record(current, AuditAction.GRADE_SUBMIT, "FRAGMENT", fragmentId,
				desc.toString(),
				prevDefinitive != null ? String.valueOf(prevDefinitive) : null,
				String.valueOf(grade));

		// A fragment is "corrected" once its task is SUBMITTED. Every time a
		// fragment is corrected, check whether the whole exam is now fully
		// corrected — i.e. every fragment has at least one SUBMITTED task —
		// and if so advance the exam from DISTRIBUTED to CORRECTED.
		maybeMarkExamCorrected(task);
		return toDto(task);
	}

	/**
	 * Resolves the on-disk path of the annotated PNG submitted with a task, for
	 * the {@code GET /correction-tasks/{id}/annotation} endpoint (the teacher
	 * revisiting a submitted task). Gated like {@link #getTask}: the assigned
	 * teacher or an admin/staff member.
	 */
	@Transactional
	public Path annotatedImagePath(long taskId) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		CorrectionTask task = correctionTaskRepository.findById(taskId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_CORRECTION_TASK, Long.toString(taskId)));
		if (!canSeeAllTasks(current) && !task.getTeacher().getId().equals(current.getId())) {
			throw new DEIException(ErrorMessage.NO_SUCH_CORRECTION_TASK, Long.toString(taskId));
		}
		String path = task.getAnnotatedImagePath();
		if (path == null) {
			throw new DEIException(ErrorMessage.ANNOTATION_NOT_FOUND);
		}
		Path resolved = Paths.get(path);
		if (!Files.exists(resolved)) {
			throw new DEIException(ErrorMessage.ANNOTATION_NOT_FOUND);
		}
		return resolved;
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
