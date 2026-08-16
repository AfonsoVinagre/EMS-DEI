package pt.ulisboa.tecnico.rnl.dei.ems.consultation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.consultation.domain.ConsultationRequest;

@Repository
@Transactional
public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, Long> {

	List<ConsultationRequest> findByExamId(long examId);

	List<ConsultationRequest> findByStudentId(long studentId);

	List<ConsultationRequest> findByExamSchoolId(long schoolId);

	List<ConsultationRequest> findByExamIdIn(List<Long> examIds);
}