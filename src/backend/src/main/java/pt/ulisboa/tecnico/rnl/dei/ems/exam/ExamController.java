package pt.ulisboa.tecnico.rnl.dei.ems.exam;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import pt.ulisboa.tecnico.rnl.dei.ems.exam.service.ExamService.SegmentAllResult;
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

	@PostMapping("/exams/{id}/segment")
	@PreAuthorize("hasAuthority('EXAM_SEGMENT')")
	public ExamDto segment(@PathVariable long id) {
		return examService.segment(id);
	}

	@PostMapping("/exams/segment-all")
	@PreAuthorize("hasAuthority('EXAM_SEGMENT')")
	public SegmentAllResult segmentAll() {
		return examService.segmentAll();
	}

	@PostMapping("/exams/{id}/distribute")
	@PreAuthorize("hasAuthority('EXAM_LOCK')")
	public ExamDto distribute(@PathVariable long id) {
		return examService.distribute(id);
	}
}