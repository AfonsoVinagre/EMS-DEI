package pt.ulisboa.tecnico.rnl.dei.ems.correction;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.CorrectionTaskDto;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.ReassignTaskRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.SubmitGradeRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.service.CorrectionService;

@RestController
public class CorrectionController {

	@Autowired
	private CorrectionService correctionService;

	@GetMapping("/correction-tasks")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<CorrectionTaskDto> listAll() {
		return correctionService.listTasks();
	}

	@GetMapping("/correction-tasks/{id}")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public CorrectionTaskDto get(@PathVariable long id) {
		return correctionService.getTask(id);
	}

	@GetMapping("/fragments/{id}/tasks")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<CorrectionTaskDto> listByFragment(@PathVariable long id) {
		return correctionService.listByFragment(id);
	}

	@PatchMapping("/correction-tasks/{id}/score")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public CorrectionTaskDto submitGrade(@PathVariable long id,
			@RequestBody SubmitGradeRequest request) {
		return correctionService.submitGrade(id, request);
	}

	@PatchMapping("/correction-tasks/{id}")
	@PreAuthorize("hasAuthority('EXAM_LOCK')")
	public CorrectionTaskDto reassign(@PathVariable long id,
			@RequestBody ReassignTaskRequest request) {
		return correctionService.reassign(id, request);
	}

	@PostMapping("/fragments/{id}/revision")
	@PreAuthorize("hasAuthority('EXAM_LOCK')")
	public CorrectionTaskDto requestRevision(@PathVariable long id,
			@RequestParam long teacherId) {
		return correctionService.requestRevision(id, teacherId);
	}
}
