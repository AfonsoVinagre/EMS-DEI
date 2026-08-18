package pt.ulisboa.tecnico.rnl.dei.ems.exam.dto;

import java.time.Instant;

import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;

public record ExamDto(
		long id,
		long studentId,
		String studentName,
		long schoolId,
		String schoolName,
		long subjectId,
		String subjectName,
		long uploadedById,
		String uploadedByName,
		String originalFilename,
		String status,
		Double grade,
		Instant releasedAt,
		Instant revisionDeadline,
		boolean revisionDistributed) {
	public ExamDto(Exam exam) {
		this(exam, null, null, null, false);
	}

	/**
	 * Builds the DTO with the exam's total grade. The grade is the sum of the
	 * definitive grades (REVISION if submitted, else PRIMARY) of every fragment
	 * of the exam; it is only meaningful once the exam is fully corrected, so
	 * callers pass {@code null} while the exam is still UPLOADED or DISTRIBUTED.
	 */
	public ExamDto(Exam exam, Double grade) {
		this(exam, grade, exam.getReleasedAt(), null, exam.isRevisionDistributed());
	}

	/**
	 * Full constructor used by {@code ExamService} to expose the per-exam
	 * revision window (Fase 5): {@code releasedAt} is when the notes became
	 * available, {@code revisionDeadline} is {@code releasedAt + windowHours}
	 * (computed by the service, which knows the configured window).
	 */
	public ExamDto(Exam exam, Double grade, Instant releasedAt,
			Instant revisionDeadline, boolean revisionDistributed) {
		this(
				exam.getId(),
				exam.getStudent().getId(),
				exam.getStudent().getName(),
				exam.getSchool().getId(),
				exam.getSchool().getName(),
				exam.getSubject().getId(),
				exam.getSubject().getName(),
				exam.getUploadedBy().getId(),
				exam.getUploadedBy().getName(),
				exam.getOriginalFilename(),
				exam.getStatus().toString(),
				grade,
				releasedAt,
				revisionDeadline,
				revisionDistributed);
	}
}