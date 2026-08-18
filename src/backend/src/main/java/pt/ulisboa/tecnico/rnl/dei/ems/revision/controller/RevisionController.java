package pt.ulisboa.tecnico.rnl.dei.ems.revision.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.dto.CreateRevisionRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.dto.RevisionRequestDto;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.service.RevisionService;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@RestController
public class RevisionController {

	@Autowired
	private RevisionService revisionService;

	/**
	 * A student files a revision request for one of their released exams:
	 * selects specific questions and provides a justification for each. Only
	 * allowed while the exam's revision window is open.
	 */
	@PostMapping("/revision-requests")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public RevisionRequestDto createRequest(@RequestBody CreateRevisionRequest body) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return revisionService.createRequest(current, body);
	}

	/** A student lists their own revision requests. */
	@GetMapping("/revision-requests/mine")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<RevisionRequestDto> listMine() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return revisionService.listMine(current);
	}

	/** School staff lists all revision requests for their school (history). */
	@GetMapping("/revision-requests")
	@PreAuthorize("hasAuthority('EXAM_RELEASE')")
	public List<RevisionRequestDto> listForStaff() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		return revisionService.listForStaff(current);
	}
}