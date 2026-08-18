package pt.ulisboa.tecnico.rnl.dei.ems.revision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.revision.domain.RevisionItem;

@Repository
@Transactional
public interface RevisionItemRepository extends JpaRepository<RevisionItem, Long> {

	List<RevisionItem> findByRevisionRequestId(long revisionRequestId);

	/** All items whose request targets the given exam — used to collect the
	 * fragments to redistribute when the exam's revision window closes. */
	List<RevisionItem> findByRevisionRequestExamId(long examId);

	List<RevisionItem> findByFragmentId(long fragmentId);
}