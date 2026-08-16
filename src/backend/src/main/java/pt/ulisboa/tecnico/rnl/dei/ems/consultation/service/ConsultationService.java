package pt.ulisboa.tecnico.rnl.dei.ems.consultation.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.consultation.domain.ConsultationRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.consultation.dto.ConsultationRequestDto;
import pt.ulisboa.tecnico.rnl.dei.ems.consultation.repository.ConsultationRequestRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.repository.ExamRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.service.ExamService;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;

/**
 * Manages student consultation requests and staff approval/rejection. The
 * actual exam release ({@code CORRECTED -> RELEASED}) is delegated to
 * {@link ExamService} so all exam-status transitions live in one place.
 */
@Service
@Transactional
public class ConsultationService {

	@Autowired
	private ConsultationRequestRepository consultationRequestRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private ExamService examService;

	/**
	 * A student files a consultation request for one of their exams. Allowed
	 * only when the exam is theirs and fully corrected, and there is no
	 * pending request already in flight for it.
	 */
	@Transactional
	public ConsultationRequestDto createRequest(Person current, long examId) {
		if (current == null || current.getType() != PersonType.STUDENT) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId)));
		// Don't reveal that another student's exam exists.
		if (!exam.getStudent().getId().equals(current.getId())) {
			throw new DEIException(ErrorMessage.NO_SUCH_EXAM, Long.toString(examId));
		}
		if (exam.getStatus() != Exam.ExamStatus.CORRECTED) {
			throw new DEIException(ErrorMessage.EXAM_NOT_CORRECTED);
		}
		boolean hasPending = consultationRequestRepository.findByExamId(examId).stream()
				.anyMatch(r -> r.getStatus() == ConsultationRequest.Status.PENDING);
		if (hasPending) {
			throw new DEIException(ErrorMessage.CONSULTATION_REQUEST_ALREADY_PENDING);
		}
		ConsultationRequest request = new ConsultationRequest(exam, current);
		return new ConsultationRequestDto(consultationRequestRepository.save(request));
	}

	@Transactional
	public List<ConsultationRequestDto> listMine(Person current) {
		if (current == null || current.getType() != PersonType.STUDENT) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		return consultationRequestRepository.findByStudentId(current.getId()).stream()
				.map(ConsultationRequestDto::new)
				.toList();
	}

	@Transactional
	public List<ConsultationRequestDto> listForStaff(Person current) {
		if (current == null || current.getType() != PersonType.SCHOOL_STAFF || current.getSchool() == null) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		return consultationRequestRepository.findByExamSchoolId(current.getSchool().getId()).stream()
				.map(ConsultationRequestDto::new)
				.toList();
	}

	@Transactional
	public ConsultationRequestDto approve(Person current, long requestId) {
		ConsultationRequest request = loadStaffOwned(current, requestId);
		if (request.getStatus() != ConsultationRequest.Status.PENDING) {
			throw new DEIException(ErrorMessage.CONSULTATION_REQUEST_NOT_PENDING);
		}
		request.setStatus(ConsultationRequest.Status.APPROVED);
		request.setDecidedAt(Instant.now());
		request.setDecidedBy(current);
		consultationRequestRepository.save(request);
		// Granting access = releasing the exam so the student can consult.
		examService.releaseExam(request.getExam().getId());
		return new ConsultationRequestDto(request);
	}

	@Transactional
	public ConsultationRequestDto reject(Person current, long requestId) {
		ConsultationRequest request = loadStaffOwned(current, requestId);
		if (request.getStatus() != ConsultationRequest.Status.PENDING) {
			throw new DEIException(ErrorMessage.CONSULTATION_REQUEST_NOT_PENDING);
		}
		request.setStatus(ConsultationRequest.Status.REJECTED);
		request.setDecidedAt(Instant.now());
		request.setDecidedBy(current);
		consultationRequestRepository.save(request);
		return new ConsultationRequestDto(request);
	}

	/**
	 * Resolves any PENDING requests for the given exams as APPROVED. Used by
	 * the bulk-release flow so a student's pending request reflects the
	 * access that was just granted in bulk.
	 */
	@Transactional
	public void approveForExams(Person current, List<Long> examIds) {
		if (examIds.isEmpty()) {
			return;
		}
		for (ConsultationRequest request : consultationRequestRepository.findByExamIdIn(examIds)) {
			if (request.getStatus() == ConsultationRequest.Status.PENDING) {
				request.setStatus(ConsultationRequest.Status.APPROVED);
				request.setDecidedAt(Instant.now());
				request.setDecidedBy(current);
				consultationRequestRepository.save(request);
			}
		}
	}

	private ConsultationRequest loadStaffOwned(Person current, long requestId) {
		if (current == null || current.getType() != PersonType.SCHOOL_STAFF || current.getSchool() == null) {
			throw new DEIException(ErrorMessage.ACCESS_DENIED);
		}
		ConsultationRequest request = consultationRequestRepository.findById(requestId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_CONSULTATION_REQUEST,
						Long.toString(requestId)));
		// A staff may only decide requests for exams of their own school.
		if (!request.getExam().getSchool().getId().equals(current.getSchool().getId())) {
			throw new DEIException(ErrorMessage.NO_SUCH_CONSULTATION_REQUEST, Long.toString(requestId));
		}
		return request;
	}
}