package pt.ulisboa.tecnico.rnl.dei.ems.systemstate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.domain.SystemState;

@Repository
@Transactional
public interface SystemStateRepository extends JpaRepository<SystemState, Long> {
}
