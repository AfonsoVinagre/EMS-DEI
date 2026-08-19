package pt.ulisboa.tecnico.rnl.dei.ems.exam;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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

import jakarta.servlet.http.HttpServletRequest;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.dto.ExamDto;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.service.ExamService;
import pt.ulisboa.tecnico.rnl.dei.ems.consultation.service.ConsultationService;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@RestController
public class ExamController {

	@Autowired
	private ExamService examService;

	@Autowired
	private ConsultationService consultationService;

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
	/**
	 * Streams the exam PDF with a dynamic watermark stamped on every page
	 * (Marcas de Água Dinâmicas): the caller's email, the download instant and
	 * the client IP. The on-disk file is untouched — the stamp is rendered per
	 * request. Used by the staff/admin exam-detail page and by the student
	 * consultation view. Students may only fetch their own released exams.
	 */
	@GetMapping("/exams/{id}/pdf")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public ResponseEntity<byte[]> getPdf(@PathVariable long id, HttpServletRequest request) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		// Students are gated to their own released exams; staff/admins pass through.
		examService.assertStudentCanViewContent(id);
		byte[] pdf = examService.watermarkedPdf(id, current, clientIp(request));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentLength(pdf.length);
		headers.setContentDispositionFormData("inline", "exame-" + id + ".pdf");
		return new ResponseEntity<>(pdf, headers, org.springframework.http.HttpStatus.OK);
	}

	/**
	 * Client IP, honoring {@code X-Forwarded-For} when a proxy/load-balancer is
	 * in front of the app (first hop); falls back to the raw remote address.
	 */
	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	/**
	 * School-staff bulk release: every CORRECTED exam of the caller's school
	 * for the given subject is made available to students (RELEASED). Returns
	 * the ids of the released exams so the UI can confirm the count.
	 */
	@PostMapping("/exams/bulk-release")
	@PreAuthorize("hasAuthority('EXAM_RELEASE')")
	public List<Long> bulkRelease(@RequestParam long subjectId) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		List<Long> released = examService.bulkRelease(current, subjectId);
		// Reflect the granted access on any pending consultation requests for
		// these exams so a student's pending request doesn't linger after the
		// exam was bulk-released.
		consultationService.approveForExams(current, released);
		return released;
	}
}