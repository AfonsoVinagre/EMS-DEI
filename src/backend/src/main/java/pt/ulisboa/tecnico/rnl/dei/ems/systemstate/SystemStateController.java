package pt.ulisboa.tecnico.rnl.dei.ems.systemstate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.dto.SystemStateDto;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.service.SystemStateService;

@RestController
public class SystemStateController {

	@Autowired
	private SystemStateService systemStateService;

	@GetMapping("/system-state")
	@PreAuthorize("isAuthenticated()")
	public SystemStateDto get() {
		return systemStateService.get();
	}

	@PostMapping("/system-state/lock")
	@PreAuthorize("hasAuthority('EXAM_LOCK')")
	public SystemStateDto lockUploads() {
		return systemStateService.lockUploads();
	}

	@PostMapping("/system-state/unlock")
	@PreAuthorize("hasAuthority('EXAM_LOCK')")
	public SystemStateDto unlockUploads() {
		return systemStateService.unlockUploads();
	}
}
