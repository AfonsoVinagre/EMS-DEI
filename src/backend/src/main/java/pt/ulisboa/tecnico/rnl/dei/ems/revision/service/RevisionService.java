package pt.ulisboa.tecnico.rnl.dei.ems.revision.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.repository.CorrectionTaskRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.domain.AuditAction;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.service.AuditService;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.repository.ExamRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository.FragmentRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.domain.RevisionItem;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.domain.RevisionRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.dto.CreateRevisionRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.dto.RevisionRequestDto;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.repository.RevisionItemRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.repository.RevisionRequestRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.domain.TeacherAssignment;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.repository.TeacherAssignmentRepository;

/**
 * Fase 5 — Revisão de Prova. Each released exam opens its own revision window
 * (releasedAt + windowHours); the student files a single request targeting
 * specific questions with justifications. When the window closes, the scheduler
 * ({@link pt.ulisboa.tecnico.rnl.dei.ems.revision.RevisionScheduler}) calls
 * {@link #processClosedWindows()} which, per exam, redistributes the targeted
 * fragments as {@code REVISION} correction tasks — ideally to a teacher other
 * than the one who did the primary correction.
 */
@Service
@Transactional
public class RevisionService {

	private static final Logger logger = LoggerFactory.getLogger(RevisionService.class);
	private static final Random RNG = new Random();

	@Autowired
	private RevisionRequestRepository revisionRequestRepository;

	@Autowired
	private RevisionItemRepository revisionItemRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private FragmentRepository fragmentRepository;

	@Autowired
	private CorrectionTaskRepository correctionTaskRepository;

	@Autowired
	private TeacherAssignmentRepository teacherAssignmentRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private AuditService auditService;

	@Value("${ems.revision.window-hours:48}")
	private long revisionWindowHours;

	// ---------------------------------------------------------------- window

	private Instant revisionDeadline(Exam exam) {
		return exam.getReleasedAt() != null
				? exam.getReleasedAt().plus(revisionWindowHours, ChronoUnit.HOURS)
				: null;
	}

	private boolean isWindowOpen(Exam exam) {
		Instant deadline = revisionDeadline(exam);
		return deadline != null
				&& !exam.isRevisionDistributed()
				&& Instant.now().isBefore(deadline);
	}

	// --------------------------------------------------------------- create

	@Transactional
	public RevisionRequestDto createRequest(Person current, CreateRevisionRequest body) {
		if (current == null || current.getType() != PersonType.STUDENT) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		Exam exam = examRepository.findById(body.examId())
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(body.examId())));
		// Don't reveal that another student's exam exists.
		if (!exam.getStudent().getId().equals(current.getId())) {
			throw new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(body.examId()));
		}
		if (exam.getStatus() != Exam.ExamStatus.RELEASED || !isWindowOpen(exam)) {
			throw new DEIException(ErrorMessage.REVISION_WINDOW_CLOSED);
		}
		// One request per (student, exam) per window.
		boolean alreadyRequested = revisionRequestRepository.findByExamId(exam.getId()).stream()
				.anyMatch(r -> r.getStudent().getId().equals(current.getId()));
		if (alreadyRequested) {
			throw new DEIException(ErrorMessage.REVISION_REQUEST_ALREADY_EXISTS);
		}

		List<CreateRevisionRequest.Item> items = body.items();
		if (items == null || items.isEmpty()) {
			throw new DEIException(ErrorMessage.REVISION_ITEM_INVALID);
		}
		// Validate each item: fragment belongs to the exam, justification non-blank.
		Set<Long> examFragmentIds = fragmentRepository.findByExamId(exam.getId()).stream()
				.map(Fragment::getId)
				.collect(Collectors.toSet());
		for (CreateRevisionRequest.Item item : items) {
			if (item.fragmentId() <= 0
					|| !examFragmentIds.contains(item.fragmentId())
					|| item.justification() == null || item.justification().isBlank()) {
				throw new DEIException(ErrorMessage.REVISION_ITEM_INVALID);
			}
		}

		RevisionRequest request = new RevisionRequest(exam, current);
		for (CreateRevisionRequest.Item item : items) {
			Fragment fragment = fragmentRepository.findById(item.fragmentId()).orElseThrow();
			request.addItem(new RevisionItem(fragment, item.justification().strip()));
		}
		revisionRequestRepository.save(request); // cascades items
		auditService.record(current, AuditAction.REVISION_REQUEST, "EXAM", exam.getId(),
				"Aluno " + current.getName() + " pediu revisão do exame " + exam.getId()
						+ " (" + items.size() + " pergunta(s))");
		return new RevisionRequestDto(request);
	}

	// ----------------------------------------------------------------- list

	@Transactional
	public List<RevisionRequestDto> listMine(Person current) {
		if (current == null || current.getType() != PersonType.STUDENT) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		return revisionRequestRepository.findByStudentId(current.getId()).stream()
				.map(RevisionRequestDto::new)
				.toList();
	}

	@Transactional
	public List<RevisionRequestDto> listForStaff(Person current) {
		if (current == null) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		// Administrators see every revision request (dashboard estatístico);
		// school staff see only their own school's requests.
		if (current.getType() == PersonType.ADMINISTRATOR) {
			return revisionRequestRepository.findAll().stream()
					.map(RevisionRequestDto::new)
					.toList();
		}
		if (current.getType() != PersonType.SCHOOL_STAFF || current.getSchool() == null) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		return revisionRequestRepository.findByExamSchoolId(current.getSchool().getId()).stream()
				.map(RevisionRequestDto::new)
				.toList();
	}

	// ------------------------------------------------------ scheduler close

	/**
	 * Finds every exam whose revision window has closed (deadline passed, not
	 * yet distributed) and processes each one in its own transaction. A failing
	 * exam (e.g. a subject with no teacher) is logged and retried on the next
	 * scheduler tick; it never blocks the other exams.
	 */
	public void processClosedWindows() {
		Instant now = Instant.now();
		List<Exam> due = examRepository.findAll().stream()
				.filter(e -> e.getReleasedAt() != null && !e.isRevisionDistributed()
						&& !now.isBefore(revisionDeadline(e)))
				.toList();
		if (due.isEmpty()) {
			return;
		}
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		for (Exam exam : due) {
			try {
				tx.executeWithoutResult(status -> processOneExam(exam.getId()));
			} catch (RuntimeException e) {
				logger.warn("Revision processing failed for exam {}: {}", exam.getId(), e.getMessage());
			}
		}
	}

	/**
	 * Per-exam close: collect the targeted fragments (from all revision items
	 * of this exam), redistribute each as a {@code REVISION} correction task
	 * (to a teacher other than the primary corrector where possible), then mark
	 * the exam {@code revisionDistributed = true}. Atomic per exam — a missing
	 * teacher pool rolls back so the scheduler retries.
	 */
	private void processOneExam(long examId) {
		Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId)));
		if (exam.isRevisionDistributed()) {
			return;
		}
		// Distinct targeted fragments, preserving first-seen order.
		Map<Long, Fragment> targets = new LinkedHashMap<>();
		for (RevisionItem item : revisionItemRepository.findByRevisionRequestExamId(examId)) {
			targets.putIfAbsent(item.getFragment().getId(), item.getFragment());
		}
		if (!targets.isEmpty()) {
			distributeRevisionForExam(exam, targets.values().stream().toList());
		}
		exam.setRevisionDistributed(true);
		examRepository.save(exam);
		auditService.recordSystem(AuditAction.REVISION_PROCESS, "EXAM", examId,
				"Janela de revisão encerrada: " + targets.size()
						+ " pergunta(s) redistribuída(s) para revisão no exame " + examId);
		logger.info("Revision window closed for exam {} ({} targeted fragment(s) redistributed)",
				examId, targets.size());
	}

	/**
	 * Mirrors {@code ExamService.distributeInternal} but only for the targeted
	 * fragments and creating {@code REVISION} tasks. For each fragment, a
	 * teacher is picked from the subject's pool, preferring one different from
	 * the teacher who did the {@code PRIMARY} task ("idealmente a professores
	 * diferentes"); if the pool has no other teacher, the primary teacher is
	 * reused as a best-effort fallback. Idempotent: skips fragments that already
	 * have a {@code REVISION} task.
	 */
	private void distributeRevisionForExam(Exam exam, List<Fragment> targets) {
		Map<Long, List<Person>> poolBySubject = new HashMap<>();
		for (Fragment fragment : targets) {
			// Skip if a revision task already exists (idempotent re-run).
			if (correctionTaskRepository
					.findByFragmentIdAndPhase(fragment.getId(), CorrectionTask.Phase.REVISION)
					.isPresent()) {
				continue;
			}
			long subjectId = fragment.getSubject().getId();
			List<Person> pool = poolBySubject.computeIfAbsent(subjectId, sid -> {
				List<TeacherAssignment> assignments = teacherAssignmentRepository.findBySubjectId(sid);
				if (assignments.isEmpty()) {
					throw new DEIException(ErrorMessage.NO_TEACHER_FOR_SUBJECT);
				}
				return assignments.stream().map(TeacherAssignment::getTeacher).toList();
			});
			// Prefer a revisor who did not do the primary correction.
			long primaryTeacherId = correctionTaskRepository.findByFragmentId(fragment.getId()).stream()
					.filter(t -> t.getPhase() == CorrectionTask.Phase.PRIMARY)
					.map(t -> t.getTeacher().getId())
					.findFirst()
					.orElse(-1L);
			List<Person> candidates = pool.stream()
					.filter(t -> t.getId() != primaryTeacherId)
					.toList();
			if (candidates.isEmpty()) {
				candidates = pool; // best-effort: only the primary teacher is available
			}
			Person chosen = candidates.get(RNG.nextInt(candidates.size()));
			correctionTaskRepository.save(
					new CorrectionTask(fragment, chosen, CorrectionTask.Phase.REVISION));
		}
	}
}