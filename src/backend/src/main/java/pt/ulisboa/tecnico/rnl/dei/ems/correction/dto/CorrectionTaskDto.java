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
		Instant submittedAt) {

	public CorrectionTaskDto(CorrectionTask task) {
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
				task.getSubmittedAt());
	}
}
