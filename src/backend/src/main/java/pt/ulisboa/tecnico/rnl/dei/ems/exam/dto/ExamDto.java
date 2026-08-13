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
		String status) {
	public ExamDto(Exam exam) {
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
				exam.getStatus().toString());
	}
}