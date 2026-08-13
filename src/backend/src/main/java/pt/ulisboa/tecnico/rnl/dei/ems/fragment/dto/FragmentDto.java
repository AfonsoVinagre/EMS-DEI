package pt.ulisboa.tecnico.rnl.dei.ems.fragment.dto;

import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;

/**
 * DTO for a fragment. Status is a derived field (computed in the service),
 * so it lives here too — exposed for table rendering in the frontend.
 */
public record FragmentDto(
		long id,
		long examId,
		int pageNumber,
		long subjectId,
		String subjectName,
		String imagePath,
		String status) {

	public FragmentDto(Fragment fragment, String status) {
		this(
				fragment.getId(),
				fragment.getExam().getId(),
				fragment.getPageNumber(),
				fragment.getSubject().getId(),
				fragment.getSubject().getName(),
				fragment.getImagePath(),
				status);
	}
}
