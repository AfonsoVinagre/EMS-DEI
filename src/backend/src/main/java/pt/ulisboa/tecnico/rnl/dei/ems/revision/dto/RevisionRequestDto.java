package pt.ulisboa.tecnico.rnl.dei.ems.revision.dto;

import java.time.Instant;
import java.util.List;

import pt.ulisboa.tecnico.rnl.dei.ems.revision.domain.RevisionItem;
import pt.ulisboa.tecnico.rnl.dei.ems.revision.domain.RevisionRequest;

/**
 * Output DTO for a revision request: the header (exam/student/subject) plus
 * the list of targeted questions, each with its justification. Exposed to the
 * student (their own requests) and to school staff (history, school-scoped).
 */
public record RevisionRequestDto(
		long id,
		long examId,
		long studentId,
		String studentName,
		long subjectId,
		String subjectName,
		Instant requestedAt,
		List<RevisionItemDto> items) {

	public record RevisionItemDto(
			long fragmentId,
			int pageNumber,
			String justification) {
		public RevisionItemDto(RevisionItem item) {
			this(item.getFragment().getId(),
					item.getFragment().getPageNumber(),
					item.getJustification());
		}
	}

	public RevisionRequestDto(RevisionRequest request) {
		this(
				request.getId(),
				request.getExam().getId(),
				request.getStudent().getId(),
				request.getStudent().getName(),
				request.getExam().getSubject().getId(),
				request.getExam().getSubject().getName(),
				request.getRequestedAt(),
				request.getItems().stream().map(RevisionItemDto::new).toList());
	}
}