package pt.ulisboa.tecnico.rnl.dei.ems.consultation.dto;

import java.time.Instant;

import pt.ulisboa.tecnico.rnl.dei.ems.consultation.domain.ConsultationRequest;

public record ConsultationRequestDto(
		long id,
		long examId,
		long studentId,
		String studentName,
		long subjectId,
		String subjectName,
		String examStatus,
		String status,
		Instant requestedAt,
		Instant decidedAt,
		String decidedByName) {

	public ConsultationRequestDto(ConsultationRequest request) {
		this(
				request.getId(),
				request.getExam().getId(),
				request.getStudent().getId(),
				request.getStudent().getName(),
				request.getExam().getSubject().getId(),
				request.getExam().getSubject().getName(),
				request.getExam().getStatus().toString(),
				request.getStatus().toString(),
				request.getRequestedAt(),
				request.getDecidedAt(),
				request.getDecidedBy() == null ? null : request.getDecidedBy().getName());
	}
}