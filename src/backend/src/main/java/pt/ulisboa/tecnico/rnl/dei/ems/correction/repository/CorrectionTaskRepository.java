package pt.ulisboa.tecnico.rnl.dei.ems.correction.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Phase;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask.Status;

@Repository
@Transactional
public interface CorrectionTaskRepository extends JpaRepository<CorrectionTask, Long> {

	List<CorrectionTask> findByFragmentId(long fragmentId);

	List<CorrectionTask> findByFragmentExamId(long examId);

	List<CorrectionTask> findByTeacherId(long teacherId);

	List<CorrectionTask> findByTeacherIdAndStatusIn(long teacherId, List<Status> statuses);

	Optional<CorrectionTask> findByFragmentIdAndPhase(long fragmentId, Phase phase);
}
