package pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.domain.TeacherAssignment;

@Repository
@Transactional
public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {
	List<TeacherAssignment> findByTeacherId(long teacherId);

	List<TeacherAssignment> findBySchoolIdAndSubjectId(long schoolId, long subjectId);

	List<TeacherAssignment> findBySubjectId(long subjectId);

	boolean existsByTeacherIdAndSchoolIdAndSubjectId(long teacherId, long schoolId, long subjectId);
}
