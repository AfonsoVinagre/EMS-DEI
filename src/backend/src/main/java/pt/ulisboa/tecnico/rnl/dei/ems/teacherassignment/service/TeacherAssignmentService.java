package pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.person.repository.PersonRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.school.repository.SchoolRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.repository.SubjectRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.domain.TeacherAssignment;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.dto.TeacherAssignmentDto;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.repository.TeacherAssignmentRepository;

// Service class for managing TeacherAssignment entities
@Service
@Transactional
public class TeacherAssignmentService {

	@Autowired
	private TeacherAssignmentRepository teacherAssignmentRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Transactional
	public TeacherAssignmentDto assign(long teacherId, long schoolId, long subjectId) {
		Person teacher = personRepository.findById(teacherId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_PERSON, Long.toString(teacherId)));

		if (teacher.getType() != PersonType.TEACHER) {
			throw new DEIException(ErrorMessage.PERSON_NOT_TEACHER, Long.toString(teacherId));
		}

		School school = schoolRepository.findById(schoolId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SCHOOL, Long.toString(schoolId)));

		Subject subject = subjectRepository.findById(subjectId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SUBJECT, Long.toString(subjectId)));

		if (teacherAssignmentRepository.existsByTeacherIdAndSchoolIdAndSubjectId(teacherId, schoolId, subjectId)) {
			throw new DEIException(ErrorMessage.TEACHER_ASSIGNMENT_ALREADY_EXISTS);
		}

		TeacherAssignment assignment = teacherAssignmentRepository.save(
				new TeacherAssignment(teacher, school, subject));
		return new TeacherAssignmentDto(assignment);
	}

	@Transactional
	public void unassign(long assignmentId) {
		TeacherAssignment assignment = teacherAssignmentRepository.findById(assignmentId)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_TEACHER_ASSIGNMENT, Long.toString(assignmentId)));
		teacherAssignmentRepository.delete(assignment);
	}

	@Transactional
	public List<TeacherAssignmentDto> listByTeacher(long teacherId) {
		return teacherAssignmentRepository.findByTeacherId(teacherId).stream()
				.map(TeacherAssignmentDto::new)
				.toList();
	}

	@Transactional
	public List<TeacherAssignmentDto> listBySchoolAndSubject(long schoolId, long subjectId) {
		return teacherAssignmentRepository.findBySchoolIdAndSubjectId(schoolId, subjectId).stream()
				.map(TeacherAssignmentDto::new)
				.toList();
	}
}
