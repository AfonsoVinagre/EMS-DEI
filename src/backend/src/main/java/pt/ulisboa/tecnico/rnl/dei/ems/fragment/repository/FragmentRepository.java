package pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;

@Repository
@Transactional
public interface FragmentRepository extends JpaRepository<Fragment, Long> {
	List<Fragment> findByExamId(long examId);

	List<Fragment> findBySubjectId(long subjectId);
}
