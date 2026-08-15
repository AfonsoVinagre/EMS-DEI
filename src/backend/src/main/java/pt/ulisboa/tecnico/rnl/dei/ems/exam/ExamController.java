package pt.ulisboa.tecnico.rnl.dei.ems.exam;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.dto.ExamDto;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.service.ExamService;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@RestController
public class ExamController {

	@Autowired
	private ExamService examService;

	@PostMapping(value = "/exams", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('EXAM_CREATE')")
	public ExamDto uploadExam(
			@RequestParam("file") MultipartFile file,
			@RequestParam long studentId,
			@RequestParam(required = false) Long schoolId,
			@RequestParam long subjectId) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return examService.uploadExam(studentId, schoolId, subjectId, current.getId(), file);
	}

	@GetMapping("/exams")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<ExamDto> listAll() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return examService.listAll(current);
	}

	@GetMapping("/exams/{id}")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public ExamDto getExam(@PathVariable long id) {
		return examService.getExam(id);
	}

	@GetMapping("/exams/student/{studentId}")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<ExamDto> listByStudent(@PathVariable long studentId) {
		return examService.listByStudent(studentId);
	}

	@GetMapping("/exams/school/{schoolId}/subject/{subjectId}")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<ExamDto> listBySchoolAndSubject(@PathVariable long schoolId, @PathVariable long subjectId) {
		return examService.listBySchoolAndSubject(schoolId, subjectId);
	}

	/**
	 * Admin-only batch distribution. Picks one teacher per fragment from the
	 * subject's teacher pool, balanced across fragments. Atomic: if any
	 * subject in the batch has no teacher, the whole transaction rolls back.
	 */
	@PostMapping("/exams/distribute-all")
	@PreAuthorize("hasAuthority('EXAM_DISTRIBUTE')")
	public List<Long> distributeAll() {
		return examService.distributeAll();
	}

	/**
	 * Streams the original (unsegmented) PDF for an exam. Used by the staff
	 * / admin exam-detail page to show the full document alongside the
	 * segmented fragments.
	 */
	@GetMapping("/exams/{id}/pdf")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public ResponseEntity<InputStreamResource> getPdf(@PathVariable long id) throws IOException {
		if (SecurityUtils.currentPerson() == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		Path pdfPath = examService.resolvePdfPath(id);
		InputStream stream = Files.newInputStream(pdfPath);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentLength(pdfPath.toFile().length());
		headers.setContentDispositionFormData("inline", pdfPath.getFileName().toString());
		return new ResponseEntity<>(new InputStreamResource(stream), headers,
				org.springframework.http.HttpStatus.OK);
	}
}