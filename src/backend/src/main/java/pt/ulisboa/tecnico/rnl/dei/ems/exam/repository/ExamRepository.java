package pt.ulisboa.tecnico.rnl.dei.ems.exam.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam.ExamStatus;

@Repository
@Transactional
public interface ExamRepository extends JpaRepository<Exam, Long> {
	List<Exam> findByStudentId(long studentId);

	List<Exam> findBySchoolIdAndSubjectId(long schoolId, long subjectId);

	List<Exam> findBySchoolIdAndSubjectIdAndStatus(long schoolId, long subjectId, ExamStatus status);

	List<Exam> findBySchoolId(long schoolId);

	boolean existsByPdfPath(String pdfPath);
}