package pt.ulisboa.tecnico.rnl.dei.ems.exam.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.repository.CorrectionTaskRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.dto.ExamDto;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.repository.ExamRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository.FragmentRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.person.repository.PersonRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.school.repository.SchoolRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.repository.SubjectRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.service.SystemStateService;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.domain.TeacherAssignment;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.repository.TeacherAssignmentRepository;

// Service class for managing Exam entities, including PDF upload to local storage.
@Service
@Transactional
public class ExamService {

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Autowired
	private FragmentRepository fragmentRepository;

	@Autowired
	private TeacherAssignmentRepository teacherAssignmentRepository;

	@Autowired
	private CorrectionTaskRepository correctionTaskRepository;

	@Autowired
	private SystemStateService systemStateService;

	@Value("${ems.storage.exams-dir}")
	private String examsDir;

	@Value("${ems.storage.fragments-dir:./data/fragments}")
	private String fragmentsDir;

	/**
	 * Length (in hours) of the per-exam revision window that opens when an exam
	 * is released (Fase 5). The deadline exposed on {@link ExamDto} is
	 * {@code releasedAt + revisionWindowHours}.
	 */
	@Value("${ems.revision.window-hours:48}")
	private long revisionWindowHours;

	private static final Random RNG = new Random();

	private void ensureStorageDirExists() {
		try {
			Files.createDirectories(Paths.get(examsDir));
		} catch (IOException e) {
			throw new DEIException(ErrorMessage.FILE_STORAGE_ERROR);
		}
	}

	private void ensureFragmentsDirExists() {
		try {
			Files.createDirectories(Paths.get(fragmentsDir));
		} catch (IOException e) {
			throw new DEIException(ErrorMessage.FILE_STORAGE_ERROR);
		}
	}

	@Transactional
	public ExamDto uploadExam(long studentId, Long schoolIdOverride, long subjectId, long uploadedById,
			MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new DEIException(ErrorMessage.INVALID_PDF_FILE);
		}
		String contentType = file.getContentType();
		String original = file.getOriginalFilename();
		boolean isPdf = (contentType != null && contentType.toLowerCase().contains("pdf"))
				|| (original != null && original.toLowerCase().endsWith(".pdf"));
		if (!isPdf) {
			throw new DEIException(ErrorMessage.INVALID_PDF_FILE);
		}

		Person student = personRepository.findById(studentId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(studentId)));
		if (student.getType() != PersonType.STUDENT) {
			throw new DEIException(ErrorMessage.PERSON_NOT_STUDENT, Long.toString(studentId));
		}

		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SUBJECT, Long.toString(subjectId)));

		Person uploadedBy = personRepository.findById(uploadedById)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(uploadedById)));

		// Staff are subject to the global upload lock. Admins retain direct
		// access (they impersonate staff to do this in practice).
		if (uploadedBy.getType() == PersonType.SCHOOL_STAFF) {
			systemStateService.assertStaffCanUpload();
		}

		// Resolve the school for this exam:
		//  - ADMINISTRATOR (no school): may target either the override school
		//    or, if none was given, the student's own school.
		//  - SCHOOL_STAFF (must have a school): override is rejected and the
		//    school's own school is forced (5025).
		School school;
		if (uploadedBy.getType() == PersonType.SCHOOL_STAFF) {
			if (uploadedBy.getSchool() == null) {
				throw new DEIException(ErrorMessage.PERSON_SCHOOL_REQUIRED);
			}
			if (schoolIdOverride != null
					&& !uploadedBy.getSchool().getId().equals(schoolIdOverride)) {
				throw new DEIException(ErrorMessage.STAFF_UPLOAD_TO_OTHER_SCHOOL);
			}
			if (student.getSchool() == null
					|| !student.getSchool().getId().equals(uploadedBy.getSchool().getId())) {
				throw new DEIException(ErrorMessage.STAFF_UPLOAD_TO_OTHER_SCHOOL);
			}
			school = uploadedBy.getSchool();
		} else {
			if (schoolIdOverride != null) {
				school = schoolRepository.findById(schoolIdOverride)
						.orElseThrow(() -> new DEIException(
								ErrorMessage.NO_SUCH_SCHOOL, Long.toString(schoolIdOverride)));
			} else if (student.getSchool() != null) {
				school = student.getSchool();
			} else {
				throw new DEIException(ErrorMessage.NO_SUCH_SCHOOL, "0");
			}
		}

		ensureStorageDirExists();

		String storedName = UUID.randomUUID() + ".pdf";
		Path target = Paths.get(examsDir).resolve(storedName);
		try {
			Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new DEIException(ErrorMessage.FILE_STORAGE_ERROR);
		}

		String relativePath = Paths.get(examsDir).resolve(storedName).toString();
		Exam exam = examRepository.save(new Exam(
				relativePath,
				original == null ? storedName : original,
				student,
				school,
				subject,
				uploadedBy));

		// Segment immediately so the user does not need to remember a
		// separate step (and so the "lock uploads" guard does not depend
		// on a batch operation they could forget to run).
		segmentInternal(exam);
		return examDto(exam, null);
	}

	/**
	 * Dev convenience: creates an {@link Exam} pointing at a PDF that is
	 * already on disk (no upload, no copy) and segments it immediately into
	 * {@link Fragment}s. Used by {@code DataInitializer} to turn PDFs sitting
	 * in the exams directory into fully fragmented exams. The exam starts in
	 * {@link Exam.ExamStatus#UPLOADED}, ready for the normal lock → distribute
	 * flow. The school is resolved from the student (as in a real upload).
	 */
	@Transactional
	public ExamDto seedExamFromDisk(String pdfPath, String originalFilename,
			long studentId, long subjectId, long uploadedById) {
		Person student = personRepository.findById(studentId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(studentId)));
		if (student.getType() != PersonType.STUDENT) {
			throw new DEIException(ErrorMessage.PERSON_NOT_STUDENT, Long.toString(studentId));
		}
		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SUBJECT, Long.toString(subjectId)));
		Person uploadedBy = personRepository.findById(uploadedById)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(uploadedById)));
		School school = student.getSchool();
		if (school == null) {
			throw new DEIException(ErrorMessage.PERSON_SCHOOL_REQUIRED);
		}
		ensureStorageDirExists();
		Exam exam = examRepository.save(
				new Exam(pdfPath, originalFilename, student, school, subject, uploadedBy));
		segmentInternal(exam);
		return examDto(exam, null);
	}

	@Transactional
	public ExamDto getExam(long id) {
		return getExam(id, SecurityUtils.currentPerson());
	}

	/**
	 * Caller-aware single-exam read. Students only ever see their own exams,
	 * and their grade is gated to {@link Exam.ExamStatus#RELEASED} (before
	 * release the total is not theirs to see). Staff/admins see the real
	 * total grade (non-null once CORRECTED/RELEASED).
	 */
	@Transactional
	public ExamDto getExam(long id, Person current) {
		Exam exam = examRepository.findById(id)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(id)));
		Double grade = (current != null && current.getType() == PersonType.STUDENT)
				? studentGrade(exam)
				: totalGrade(exam);
		if (current != null && current.getType() == PersonType.STUDENT
				&& !exam.getStudent().getId().equals(current.getId())) {
			// Don't reveal that another student's exam exists.
			throw new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(id));
		}
		return examDto(exam, grade);
	}

	/**
	 * Resolves the on-disk path to the original PDF for an exam.
	 * Throws if the file is missing so the controller can return 404.
	 */
	@Transactional
	public java.nio.file.Path resolvePdfPath(long examId) {
		Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId)));
		java.nio.file.Path pdfPath = Paths.get(exam.getPdfPath());
		if (!Files.exists(pdfPath)) {
			throw new DEIException(ErrorMessage.PDF_RENDER_ERROR);
		}
		return pdfPath;
	}

	/**
	 * Returns the exam PDF with a <em>dynamic</em> watermark stamped on every
	 * page, identifying who is downloading and when: the caller's email, the
	 * download instant and the client IP. The on-disk file is left untouched —
	 * the stamp is rendered in-memory per request, so each download carries a
	 * unique trace (this is the "Marcas de Água Dinâmicas" feature: prevents
	 * improper sharing and traces leaks).
	 *
	 * <p>If stamping fails for any reason, falls back to the original bytes so
	 * the watermark never blocks access to the exam.
	 *
	 * <p>Only the full PDF is watermarked — fragment images are NOT, because
	 * they are served to teachers (blind correction) and would reveal the
	 * student's identity.
	 */
	@Transactional
	public byte[] watermarkedPdf(long examId, Person current, String clientIp) {
		java.nio.file.Path pdfPath = resolvePdfPath(examId);
		String email = (current != null && current.getEmail() != null) ? current.getEmail() : "anonymous";
		String when = Instant.now().toString();
		String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp : "unknown";
		String footer = "EMS · " + email + " · " + when + " · IP " + ip;

		float rad = (float) Math.toRadians(45);
		float cos = (float) Math.cos(rad);
		float sin = (float) Math.sin(rad);

		try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
			PDType1Font bold = PDType1Font.HELVETICA_BOLD;
			PDType1Font reg = PDType1Font.HELVETICA;
			for (PDPage page : doc.getPages()) {
				PDRectangle box = page.getMediaBox();
				float w = box.getWidth();
				float h = box.getHeight();
				try (PDPageContentStream cs = new PDPageContentStream(doc, page,
						PDPageContentStream.AppendMode.APPEND, true, true)) {
					// Diagonal, semi-transparent email across the middle (deterrent).
					PDExtendedGraphicsState diag = new PDExtendedGraphicsState();
					diag.setNonStrokingAlphaConstant(0.25f);
					diag.setStrokingAlphaConstant(0.25f);
					cs.setGraphicsStateParameters(diag);
					cs.setNonStrokingColor(200, 200, 200);
					cs.beginText();
					float fontSize = 48f;
					cs.setFont(bold, fontSize);
					// Center the rotated text on the page: the text origin sits at
					// (cx - halfWidth*cos, cy - halfWidth*sin) so that, extending
					// textWidth along the rotated x-axis (cos, sin), its midpoint
					// lands exactly at the page center (w/2, h/2). getStringWidth
					// is in 1000-unit glyph space → scale by fontSize/1000.
					float textWidth = bold.getStringWidth(email) / 1000f * fontSize;
					float cx = w / 2f;
					float cy = h / 2f;
					float tx = cx - (textWidth / 2f) * cos;
					float ty = cy - (textWidth / 2f) * sin;
					// Rotation matrix (version-safe in PDFBox 2.x): [cos sin -sin cos tx ty].
					cs.setTextMatrix(new Matrix(cos, sin, -sin, cos, tx, ty));
					cs.showText(email);
					cs.endText();

					// Footer trace line: email · date · IP, bottom of every page.
					PDExtendedGraphicsState foot = new PDExtendedGraphicsState();
					foot.setNonStrokingAlphaConstant(0.55f);
					cs.setGraphicsStateParameters(foot);
					cs.setNonStrokingColor(90, 90, 90);
					cs.beginText();
					cs.setFont(reg, 8);
					// Reset the text matrix (the diagonal above left it rotated).
					cs.setTextMatrix(new Matrix(1, 0, 0, 1, 20, 18));
					cs.showText(footer);
					cs.endText();
				}
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			doc.save(baos);
			return baos.toByteArray();
		} catch (IOException e) {
			// Stamping failed: fall back to the untouched original so the
			// watermark never blocks access to the exam.
			try {
				return Files.readAllBytes(pdfPath);
			} catch (IOException ioe) {
				throw new DEIException(ErrorMessage.PDF_RENDER_ERROR);
			}
		}
	}

	@Transactional
	public List<ExamDto> listAll() {
		return examRepository.findAll().stream()
				.map(exam -> examDto(exam, totalGrade(exam)))
				.toList();
	}

	/**
	 * Scoped listing for the requesting user. Admins see every exam; school
	 * staff see only the exams belonging to their own school; students see
	 * only their own exams (with the grade hidden until RELEASED). Teachers
	 * fall back to the unfiltered list (read-only).
	 */
	@Transactional
	public List<ExamDto> listAll(Person current) {
		if (current.getType() == PersonType.ADMINISTRATOR) {
			return listAll();
		}
		if (current.getType() == PersonType.SCHOOL_STAFF) {
			if (current.getSchool() == null) {
				throw new DEIException(ErrorMessage.PERSON_SCHOOL_REQUIRED);
			}
			return examRepository.findBySchoolId(current.getSchool().getId()).stream()
					.map(exam -> examDto(exam, totalGrade(exam)))
					.toList();
		}
		if (current.getType() == PersonType.STUDENT) {
			return examRepository.findByStudentId(current.getId()).stream()
					.map(exam -> examDto(exam, studentGrade(exam)))
					.toList();
		}
		return listAll();
	}

	@Transactional
	public List<ExamDto> listByStudent(long studentId) {
		return examRepository.findByStudentId(studentId).stream()
				.map(exam -> examDto(exam, totalGrade(exam)))
				.toList();
	}

	@Transactional
	public List<ExamDto> listBySchoolAndSubject(long schoolId, long subjectId) {
		return examRepository.findBySchoolIdAndSubjectId(schoolId, subjectId).stream()
				.map(exam -> examDto(exam, totalGrade(exam)))
				.toList();
	}

	/**
	 * Performs the actual segmentation work for a freshly UPLOADED exam.
	 * Throws a {@link DEIException} on PDF errors; partial PNG fragments
	 * are cleaned up before propagating.
	 *
	 * No auto-attribution: this is purely a physical carve-out. Distribution
	 * happens later via {@link #distribute(long)}.
	 */
	private void segmentInternal(Exam exam) {
		ensureFragmentsDirExists();

		Path pdfPath = Paths.get(exam.getPdfPath());
		if (!Files.exists(pdfPath)) {
			throw new DEIException(ErrorMessage.PDF_RENDER_ERROR);
		}

		List<Path> producedFiles = new ArrayList<>();
		try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
			PDFRenderer renderer = new PDFRenderer(document);
			int pageCount = document.getNumberOfPages();
			for (int i = 0; i < pageCount; i++) {
				int pageNumber = i + 1;
				String fileName = exam.getId() + "_p" + pageNumber + ".png";
				Path fragmentFile = Paths.get(fragmentsDir).resolve(fileName);
				BufferedImage image = renderer.renderImageWithDPI(i, 150);
				ImageIO.write(image, "PNG", fragmentFile.toFile());
				producedFiles.add(fragmentFile);

				Fragment fragment = new Fragment(exam, pageNumber, fileName, exam.getSubject());
				fragmentRepository.save(fragment);
			}
		} catch (IOException e) {
			for (Path file : producedFiles) {
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// best-effort cleanup
				}
			}
			throw new DEIException(ErrorMessage.PDF_RENDER_ERROR);
		}

		exam.setStatus(Exam.ExamStatus.UPLOADED);
		examRepository.save(exam);
	}

	/**
	 * Distribute every fragment of an exam across the available teachers for
	 * its subject. Each fragment gets exactly one PRIMARY CorrectionTask
	 * assigned to a random teacher from that subject's pool. The pool is
	 * pre-loaded once per subject so the random selection distributes the
	 * exam's load evenly across the available teachers.
	 *
	 * Fails atomically (the whole transaction rolls back) if any subject
	 * present in the exam has no teacher assigned.
	 */
	private void distributeInternal(Exam exam) {
		List<Fragment> fragments = fragmentRepository.findByExamId(exam.getId());
		if (fragments.isEmpty()) {
			throw new DEIException(ErrorMessage.EXAM_NOT_DISTRIBUTABLE, "no fragments");
		}

		// Pre-load teacher pools per subject — guarantees fairness across
		// fragments of the same exam: every fragment of subject X shares the
		// same teacher pool, so random selection distributes load evenly.
		java.util.Map<Long, List<Person>> teachersBySubject = new java.util.HashMap<>();

		for (Fragment fragment : fragments) {
			long subjectId = fragment.getSubject().getId();
			List<Person> pool = teachersBySubject.computeIfAbsent(subjectId, sid -> {
				List<TeacherAssignment> assignments = teacherAssignmentRepository.findBySubjectId(sid);
				if (assignments.isEmpty()) {
					throw new DEIException(ErrorMessage.NO_TEACHER_FOR_SUBJECT);
				}
				return assignments.stream().map(TeacherAssignment::getTeacher).toList();
			});
			Person chosen = pool.get(RNG.nextInt(pool.size()));
			correctionTaskRepository.save(new CorrectionTask(fragment, chosen, CorrectionTask.Phase.PRIMARY));
		}

		exam.setStatus(Exam.ExamStatus.DISTRIBUTED);
		examRepository.save(exam);
	}

	/**
	 * Distribute every UPLOADED exam in the system. Used by the admin-only
	 * "Distribute exams" action. Returns the list of distributed exam ids
	 * for the UI to display a summary.
	 *
	 * Distribution is only allowed once the system is locked — uploads are
	 * frozen, the admin reviews the pool and triggers the batch. A single
	 * exam failing (e.g. a subject with no teacher) rolls the whole batch
	 * back: the admin is told to fix the assignment and retry.
	 */
	@Transactional
	public List<Long> distributeAll() {
		if (!systemStateService.isLocked()) {
			throw new DEIException(ErrorMessage.EXAMS_NOT_LOCKED);
		}
		List<Exam> uploaded = examRepository.findAll().stream()
				.filter(e -> e.getStatus() == Exam.ExamStatus.UPLOADED)
				.toList();
		for (Exam exam : uploaded) {
			distributeInternal(exam);
		}
		if (!uploaded.isEmpty()) {
			// Permanent marker: from now on, the lock cannot be reversed.
			systemStateService.markDistributed();
		}
		return uploaded.stream().map(Exam::getId).toList();
	}

	/**
	 * Sum of the <em>definitive</em> grade of every fragment of an exam. The
	 * definitive grade of a fragment is the grade of its {@code REVISION} task
	 * if that has been submitted, otherwise the grade of its {@code PRIMARY}
	 * task. Returns {@code null} unless the exam is fully corrected — i.e. its
	 * status is {@link Exam.ExamStatus#CORRECTED} or {@link
	 * Exam.ExamStatus#RELEASED}: before that the total is not meaningful and the
	 * UI renders "--".
	 *
	 * <p>Grouping per fragment (rather than summing every SUBMITTED task) avoids
	 * double-counting once a revisão creates a second SUBMITTED task on a
	 * fragment that already had its PRIMARY submitted.
	 */
	private Double totalGrade(Exam exam) {
		if (exam.getStatus() != Exam.ExamStatus.CORRECTED
				&& exam.getStatus() != Exam.ExamStatus.RELEASED) {
			return null;
		}
		List<Fragment> fragments = fragmentRepository.findByExamId(exam.getId());
		if (fragments.isEmpty()) {
			return null;
		}
		Map<Long, List<CorrectionTask>> tasksByFragment = correctionTaskRepository
				.findByFragmentExamId(exam.getId()).stream()
				.collect(Collectors.groupingBy(t -> t.getFragment().getId()));
		double total = 0;
		for (Fragment fragment : fragments) {
			Double definitive = definitiveGrade(
					tasksByFragment.getOrDefault(fragment.getId(), List.of()));
			if (definitive == null) {
				return null;
			}
			total += definitive;
		}
		return total;
	}

	/**
	 * The definitive grade of a fragment: the grade of its submitted
	 * {@code REVISION} task if any, otherwise the grade of its submitted
	 * {@code PRIMARY} task. Mirrors {@code FragmentService.computeGrade} (kept
	 * in sync by design — the fragment-level and exam-level views must agree).
	 */
	private Double definitiveGrade(List<CorrectionTask> tasks) {
		Double revision = tasks.stream()
				.filter(t -> t.getPhase() == CorrectionTask.Phase.REVISION
						&& t.getStatus() == CorrectionTask.Status.SUBMITTED)
				.map(CorrectionTask::getGrade)
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
		if (revision != null) {
			return revision;
		}
		return tasks.stream()
				.filter(t -> t.getPhase() == CorrectionTask.Phase.PRIMARY
						&& t.getStatus() == CorrectionTask.Status.SUBMITTED)
				.map(CorrectionTask::getGrade)
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Builds the {@link ExamDto} for an exam, exposing the per-exam revision
	 * window: {@code revisionDeadline = releasedAt + revisionWindowHours} (or
	 * {@code null} before release). The grade is whatever the caller computed
	 * (student-gated or the real total).
	 */
	private ExamDto examDto(Exam exam, Double grade) {
		Instant releasedAt = exam.getReleasedAt();
		Instant deadline = releasedAt != null
				? releasedAt.plus(revisionWindowHours, ChronoUnit.HOURS)
				: null;
		return new ExamDto(exam, grade, releasedAt, deadline, exam.isRevisionDistributed());
	}

	/**
	 * Marks an exam as released and stamps {@code releasedAt} — the start of
	 * the per-exam revision window. Centralised so both the individual approve
	 * flow and the bulk release stamp the same timestamp.
	 */
	private void markReleased(Exam exam) {
		exam.setStatus(Exam.ExamStatus.RELEASED);
		if (exam.getReleasedAt() == null) {
			exam.setReleasedAt(Instant.now());
		}
		examRepository.save(exam);
	}

	/**
	 * Grade as seen by a student: hidden until the exam is released. Once
	 * {@link Exam.ExamStatus#RELEASED}, matches {@link #totalGrade(Exam)}.
	 */
	private Double studentGrade(Exam exam) {
		if (exam.getStatus() != Exam.ExamStatus.RELEASED) {
			return null;
		}
		return totalGrade(exam);
	}

	/**
	 * Releases a corrected exam: {@code CORRECTED -> RELEASED}, stamping
	 * {@code releasedAt} (the start of the per-exam revision window). Idempotent
	 * for an already-released exam (preserves the original {@code releasedAt});
	 * throws if the exam is not yet fully corrected.
	 */
	@Transactional
	public void releaseExam(long examId) {
		Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId)));
		if (exam.getStatus() == Exam.ExamStatus.RELEASED) {
			return;
		}
		if (exam.getStatus() != Exam.ExamStatus.CORRECTED) {
			throw new DEIException(ErrorMessage.EXAM_NOT_CORRECTED);
		}
		markReleased(exam);
	}

	/**
	 * Bulk release: every CORRECTED exam of the caller's school for the given
	 * subject transitions to RELEASED (stamping {@code releasedAt}). Returns
	 * the ids of the released exams (the staff UI reports how many were made
	 * available).
	 */
	@Transactional
	public List<Long> bulkRelease(Person current, long subjectId) {
		if (current.getType() != PersonType.SCHOOL_STAFF || current.getSchool() == null) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		List<Exam> corrected = examRepository.findBySchoolIdAndSubjectIdAndStatus(
				current.getSchool().getId(), subjectId, Exam.ExamStatus.CORRECTED);
		for (Exam exam : corrected) {
			markReleased(exam);
		}
		return corrected.stream().map(Exam::getId).toList();
	}

	/**
	 * Guard for content endpoints (PDF, fragment images) a student should only
	 * reach for their own released exams. Throws {@code NO_SUCH_EXAM} (so as
	 * not to reveal existence) if the caller is a student who does not own the
	 * exam or whose exam is not yet released.
	 */
	@Transactional
	public void assertStudentCanViewContent(long examId) {
		Person current = SecurityUtils.currentPerson();
		if (current == null || current.getType() != PersonType.STUDENT) {
			return;
		}
		Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId)));
		if (!exam.getStudent().getId().equals(current.getId())
				|| exam.getStatus() != Exam.ExamStatus.RELEASED) {
			throw new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId));
		}
	}
}
