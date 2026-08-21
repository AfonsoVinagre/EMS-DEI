package pt.ulisboa.tecnico.rnl.dei.ems.audit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.audit.domain.AuditLog;

/**
 * Repository for {@link AuditLog}. <b>Append-only by design</b>: it exposes
 * only {@code save} (inherited) and read methods — there is no {@code delete}
 * or {@code update} query, so the API has no mutation path. This is the first
 * layer of the log's inviolability; the second is the SHA-256 hash chain
 * maintained by {@code AuditService}.
 */
@Repository
@Transactional
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	/** Most recent entry, for chaining the next record's {@code prevHash}. */
	Optional<AuditLog> findTopByOrderByIdDesc();
}