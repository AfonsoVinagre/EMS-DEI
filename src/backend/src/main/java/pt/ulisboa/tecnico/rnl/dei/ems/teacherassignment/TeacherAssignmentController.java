package pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.dto.AssignTeacherRequest;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.dto.TeacherAssignmentDto;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.service.TeacherAssignmentService;

@RestController
public class TeacherAssignmentController {

	@Autowired
	private TeacherAssignmentService teacherAssignmentService;

	@PostMapping("/teacher-assignments")
	@PreAuthorize("hasAuthority('TEACHER_ASSIGNMENT_MANAGE')")
	public TeacherAssignmentDto assign(@RequestBody AssignTeacherRequest request) {
		return teacherAssignmentService.assign(request.teacherId(), request.schoolId(), request.subjectId());
	}

	@DeleteMapping("/teacher-assignments/{id}")
	@PreAuthorize("hasAuthority('TEACHER_ASSIGNMENT_MANAGE')")
	public void unassign(@PathVariable long id) {
		teacherAssignmentService.unassign(id);
	}

	@GetMapping("/teacher-assignments")
	@PreAuthorize("hasAuthority('TEACHER_ASSIGNMENT_READ')")
	public List<TeacherAssignmentDto> listAll() {
		return teacherAssignmentService.listAll();
	}

	@GetMapping("/teacher-assignments/teacher/{teacherId}")
	@PreAuthorize("hasAuthority('TEACHER_ASSIGNMENT_READ')")
	public List<TeacherAssignmentDto> listByTeacher(@PathVariable long teacherId) {
		return teacherAssignmentService.listByTeacher(teacherId);
	}

	@GetMapping("/teacher-assignments/school/{schoolId}/subject/{subjectId}")
	@PreAuthorize("hasAuthority('TEACHER_ASSIGNMENT_READ')")
	public List<TeacherAssignmentDto> listBySchoolAndSubject(@PathVariable long schoolId, @PathVariable long subjectId) {
		return teacherAssignmentService.listBySchoolAndSubject(schoolId, subjectId);
	}
}