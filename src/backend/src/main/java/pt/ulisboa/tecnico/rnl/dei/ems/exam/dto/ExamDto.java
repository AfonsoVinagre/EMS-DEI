package pt.ulisboa.tecnico.rnl.dei.ems.exam.dto;

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
		Double grade) {
	public ExamDto(Exam exam) {
		this(exam, null);
	}

	/**
	 * Builds the DTO with the exam's total grade. The grade is the sum of the
	 * grades of every corrected (SUBMITTED) fragment of the exam; it is only
	 * meaningful once the exam is fully corrected, so callers pass {@code null}
	 * while the exam is still UPLOADED or DISTRIBUTED.
	 */
	public ExamDto(Exam exam, Double grade) {
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
				grade);
	}
}