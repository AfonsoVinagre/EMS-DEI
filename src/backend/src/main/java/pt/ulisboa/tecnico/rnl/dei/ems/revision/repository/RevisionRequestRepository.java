package pt.ulisboa.tecnico.rnl.dei.ems.revision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.revision.domain.RevisionRequest;

@Repository
@Transactional
public interface RevisionRequestRepository extends JpaRepository<RevisionRequest, Long> {

	List<RevisionRequest> findByStudentId(long studentId);

	List<RevisionRequest> findByExamId(long examId);

	List<RevisionRequest> findByExamSchoolId(long schoolId);

	List<RevisionRequest> findByExamIdIn(List<Long> examIds);
}