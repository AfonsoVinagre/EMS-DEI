package pt.ulisboa.tecnico.rnl.dei.ems.correction.dto;

import java.time.Instant;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Phase;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Status;

public record CorrectionTaskDto(
		long id,
		long fragmentId,
		int fragmentPageNumber,
		long examId,
		long subjectId,
		String subjectName,
		long teacherId,
		String teacherName,
		Phase phase,
		Status status,
		Double grade,
		Instant assignedAt,
		Instant submittedAt,
		String revisionJustification) {

	/**
	 * Builds the DTO without a revision justification (PRIMARY tasks, or any
	 * caller that does not need to surface the student's justification).
	 */
	public CorrectionTaskDto(CorrectionTask task) {
		this(task, null);
	}

	/**
	 * Builds the DTO, optionally attaching the student's revision justification.
	 * Only meaningful for {@code REVISION} tasks: the justification is fetched
	 * from the matching {@code RevisionItem} and exposed read-only to the
	 * revising teacher. Never persisted on the task itself.
	 */
	public CorrectionTaskDto(CorrectionTask task, String revisionJustification) {
		this(
				task.getId(),
				task.getFragment().getId(),
				task.getFragment().getPageNumber(),
				task.getFragment().getExam().getId(),
				task.getFragment().getSubject().getId(),
				task.getFragment().getSubject().getName(),
				task.getTeacher().getId(),
				task.getTeacher().getName(),
				task.getPhase(),
				task.getStatus(),
				task.getGrade(),
				task.getAssignedAt(),
				task.getSubmittedAt(),
				revisionJustification);
	}
}