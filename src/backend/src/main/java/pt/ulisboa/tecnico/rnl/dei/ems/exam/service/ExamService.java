package pt.ulisboa.tecnico.rnl.dei.ems.exam.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.dto.ExamDto;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.repository.ExamRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.person.repository.PersonRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.school.repository.SchoolRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.repository.SubjectRepository;

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

	@Value("${ems.storage.exams-dir}")
	private String examsDir;

	private void ensureStorageDirExists() {
		try {
			Files.createDirectories(Paths.get(examsDir));
		} catch (IOException e) {
			throw new DEIException(ErrorMessage.FILE_STORAGE_ERROR);
		}
	}

	@Transactional
	public ExamDto uploadExam(long studentId, long schoolId, long subjectId, long uploadedById,
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

		School school = schoolRepository.findById(schoolId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SCHOOL, Long.toString(schoolId)));

		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SUBJECT, Long.toString(subjectId)));

		Person uploadedBy = personRepository.findById(uploadedById)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(uploadedById)));

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
}