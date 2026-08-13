package pt.ulisboa.tecnico.rnl.dei.ems.exam.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
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
		return new ExamDto(exam);
	}

	@Transactional
	public ExamDto getExam(long id) {
		return examRepository.findById(id)
				.map(ExamDto::new)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(id)));
	}

	@Transactional
	public List<ExamDto> listAll() {
		return examRepository.findAll().stream()
				.map(ExamDto::new)
				.toList();
	}

	/**
	 * Scoped listing for the requesting user. Admins see every exam; school
	 * staff see only the exams belonging to their own school; teachers and
	 * students fall back to the unfiltered list (read-only).
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
					.map(ExamDto::new)
					.toList();
		}
		return listAll();
	}

	@Transactional
	public List<ExamDto> listByStudent(long studentId) {
		return examRepository.findByStudentId(studentId).stream()
				.map(ExamDto::new)
				.toList();
	}

	@Transactional
	public List<ExamDto> listBySchoolAndSubject(long schoolId, long subjectId) {
		return examRepository.findBySchoolIdAndSubjectId(schoolId, subjectId).stream()
				.map(ExamDto::new)
				.toList();
	}

	/**
	 * Splits an exam's PDF into one Fragment per page, rendered to PNG at 150
	 * DPI. Atomic: if any page fails, the exam stays UPLOADED and partial
	 * files are removed.
	 *
	 * No auto-attribution: this is purely a physical carve-out. Distribution
	 * happens later via {@link #distribute(long)}.
	 */
	@Transactional
	public ExamDto segment(long examId) {
		Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId)));

		if (exam.getStatus() != Exam.ExamStatus.UPLOADED) {
			throw new DEIException(ErrorMessage.EXAM_NOT_SEGMENTABLE, exam.getStatus().toString());
		}

		segmentInternal(exam);
		return new ExamDto(exam);
	}

	/**
	 * Performs the actual segmentation work for an exam that is already
	 * known to be UPLOADED. Caller is responsible for the status check.
	 * Throws a {@link DEIException} on PDF errors.
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

		exam.setStatus(Exam.ExamStatus.SEGMENTED);
		examRepository.save(exam);
	}

	/**
	 * Result of a batch segmentation run. {@link #examIds} contains the IDs
	 * of exams that were successfully moved to SEGMENTED; {@link #failures}
	 * groups the ones that failed along with the ErrorMessage code that
	 * surfaced.
	 */
	public record SegmentAllResult(List<Long> examIds, List<SegmentFailure> failures) {
	}

	public record SegmentFailure(long examId, int errorCode) {
	}

	/**
	 * Segment every UPLOADED exam in the database. Each exam is processed in
	 * its own transaction (REQUIRES_NEW) so that a single bad PDF does not
	 * block the rest of the batch.
	 */
	@Transactional
	public SegmentAllResult segmentAll() {
		List<Exam> uploaded = examRepository.findByStatus(Exam.ExamStatus.UPLOADED);
		List<Long> segmented = new ArrayList<>();
		List<SegmentFailure> failures = new ArrayList<>();
		for (Exam exam : uploaded) {
			try {
				segmentInternal(exam);
				segmented.add(exam.getId());
			} catch (DEIException ex) {
				failures.add(new SegmentFailure(exam.getId(), ex.getErrorMessage().getCode()));
			}
		}
		return new SegmentAllResult(segmented, failures);
	}

	/**
	 * Distribute a segmented exam: for every fragment, pick a random teacher
	 * covering the subject (school ignored per product decision) and create a
	 * PRIMARY CorrectionTask. Fails atomically if any subject has no teacher.
	 */
	@Transactional
	public ExamDto distribute(long examId) {
		Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId)));

		if (exam.getStatus() != Exam.ExamStatus.SEGMENTED) {
			throw new DEIException(ErrorMessage.EXAM_NOT_DISTRIBUTABLE, exam.getStatus().toString());
		}

		List<Fragment> fragments = fragmentRepository.findByExamId(examId);
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
		return new ExamDto(exam);
	}
}
