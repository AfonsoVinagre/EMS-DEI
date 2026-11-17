package pt.ulisboa.tecnico.rnl.dei.ems.fragment;

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
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.dto.FragmentDto;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository.FragmentRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.service.FragmentService;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

@RestController
public class FragmentController {

	@Autowired
	private FragmentService fragmentService;

	@Autowired
	private FragmentRepository fragmentRepository;

	@GetMapping("/fragments")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<FragmentDto> listAll() {
		return fragmentService.listAll();
	}

	@GetMapping("/fragments/{id}")
	@PreAuthorize("hasAuthority('EXAM_READ') and @fragmentAuthorization.canReadFragment(#id)")
	public FragmentDto get(@PathVariable long id) {
		return fragmentService.get(id);
	}

	@GetMapping("/fragments/exam/{examId}")
	@PreAuthorize("hasAuthority('EXAM_READ')")
	public List<FragmentDto> listByExam(@PathVariable long examId) {
		return fragmentService.listByExam(examId);
	}

	@GetMapping("/fragments/{id}/image")
	@PreAuthorize("hasAuthority('EXAM_READ') and @fragmentAuthorization.canReadFragment(#id)")
	public ResponseEntity<InputStreamResource> getImage(@PathVariable long id) throws IOException {
		if (SecurityUtils.currentPerson() == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		Fragment fragment = fragmentRepository.findById(id)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(id)));
		// Reuse the service's filter logic by calling get() — cheap because
		// findById is in the persistence context already.
		fragmentService.get(id);

		Path imagePath = fragmentService.resolveImagePath(fragment);
		InputStream stream = fragmentService.openImage(fragment);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setContentLength(imagePath.toFile().length());
		return new ResponseEntity<>(new InputStreamResource(stream), headers,
				org.springframework.http.HttpStatus.OK);
	}

	/**
	 * The annotated version of a fragment image — the composited PNG submitted
	 * with the fragment's definitive correction task (REVISION if any, else
	 * PRIMARY). This is what the student sees in consultation instead of the
	 * raw image. Returns 400 (ANNOTATION_NOT_FOUND) when there is no
	 * annotation, so the frontend can fall back to {@code getImage}.
	 */
	@GetMapping("/fragments/{id}/annotated-image")
	@PreAuthorize("hasAuthority('EXAM_READ') and @fragmentAuthorization.canReadDefinitiveAnnotation(#id)")
	public ResponseEntity<InputStreamResource> getAnnotatedImage(@PathVariable long id) throws IOException {
		if (SecurityUtils.currentPerson() == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		Path path = fragmentService.resolveDefinitiveAnnotatedImagePath(id);
		InputStream stream = Files.newInputStream(path);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.IMAGE_PNG);
		headers.setContentLength(path.toFile().length());
		return new ResponseEntity<>(new InputStreamResource(stream), headers,
				org.springframework.http.HttpStatus.OK);
	}
}
