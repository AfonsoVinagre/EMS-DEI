package pt.ulisboa.tecnico.rnl.dei.ems.consultation.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.consultation.dto.ConsultationRequestDto;
import pt.ulisboa.tecnico.rnl.dei.ems.consultation.service.ConsultationService;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@RestController
public class ConsultationController {

	@Autowired
	private ConsultationService consultationService;

	/** A student files a consultation request for one of their corrected exams. */
	@PostMapping("/consultation-requests")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public ConsultationRequestDto createRequest(@RequestBody CreateConsultationRequest body) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return consultationService.createRequest(current, body.examId());
	}

	/** A student lists their own consultation requests. */
	@GetMapping("/consultation-requests/mine")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<ConsultationRequestDto> listMine() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return consultationService.listMine(current);
	}

	/** School staff lists all consultation requests for their school. */
	@GetMapping("/consultation-requests")
	@PreAuthorize("hasAuthority('EXAM_RELEASE')")
	public List<ConsultationRequestDto> listForStaff() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return consultationService.listForStaff(current);
	}

	@PostMapping("/consultation-requests/{id}/approve")
	@PreAuthorize("hasAuthority('EXAM_RELEASE')")
	public ConsultationRequestDto approve(@PathVariable long id) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return consultationService.approve(current, id);
	}

	@PostMapping("/consultation-requests/{id}/reject")
	@PreAuthorize("hasAuthority('EXAM_RELEASE')")
	public ConsultationRequestDto reject(@PathVariable long id) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return consultationService.reject(current, id);
	}

	public record CreateConsultationRequest(long examId) {
	}
}