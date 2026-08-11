package pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.dto;

import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.domain.TeacherAssignment;

public record TeacherAssignmentDto(
		long id,
		long teacherId,
		String teacherName,
		long schoolId,
		String schoolName,
		long subjectId,
		String subjectName) {
	public TeacherAssignmentDto(TeacherAssignment ta) {
		this(
				ta.getId(),
				ta.getTeacher().getId(),
				ta.getTeacher().getName(),
				ta.getSchool().getId(),
				ta.getSchool().getName(),
				ta.getSubject().getId(),
				ta.getSubject().getName());
	}
}
