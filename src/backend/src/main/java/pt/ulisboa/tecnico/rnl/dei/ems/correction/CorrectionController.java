package pt.ulisboa.tecnico.rnl.dei.ems.correction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.CorrectionTaskDto;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.dto.ReassignTaskRequest;
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

	/**
	 * Teacher submits a grade (multipart): the grade plus the optional
	 * composited PNG annotation produced by the canvas tool. Multipart so the
	 * annotation is uploaded atomically with the grade in a single request.
	 */
	@PatchMapping("/correction-tasks/{id}/score")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public CorrectionTaskDto submitGrade(@PathVariable long id,
			@RequestParam Double grade,
			@RequestParam(required = false) MultipartFile annotation) {
		return correctionService.submitGrade(id, grade, annotation);
	}

	/**
	 * The annotated PNG submitted with a task — served back to the teacher
	 * revisiting a submitted correction (read-only review of their annotation).
	 */
	@GetMapping("/correction-tasks/{id}/annotation")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public ResponseEntity<InputStreamResource> getAnnotation(@PathVariable long id) throws IOException {
		Path path = correctionService.annotatedImagePath(id);
		InputStream stream = java.nio.file.Files.newInputStream(path);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setContentLength(path.toFile().length());
		return new ResponseEntity<>(new InputStreamResource(stream), headers,
				org.springframework.http.HttpStatus.OK);
	}

	@PatchMapping("/correction-tasks/{id}")
	@PreAuthorize("hasAuthority('EXAM_LOCK')")
	public CorrectionTaskDto reassign(@PathVariable long id,
			@RequestBody ReassignTaskRequest request) {
		return correctionService.reassign(id, request);
	}
}
