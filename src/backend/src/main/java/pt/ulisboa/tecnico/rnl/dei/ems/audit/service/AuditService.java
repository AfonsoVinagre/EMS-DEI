package pt.ulisboa.tecnico.rnl.dei.ems.audit.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.audit.domain.AuditAction;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.domain.AuditLog;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.dto.AuditLogDto;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.dto.IntegrityReportDto;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.repository.AuditLogRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;

/**
 * Registo de Auditoria Inviolável. Records every critical action as an
 * append-only {@link AuditLog} entry, chained by SHA-256 hashes so any
 * in-database tampering is detectable.
 *
 * <p>{@code record(...)} uses the default transaction propagation (REQUIRED),
 * so it joins the caller's transaction: the audit entry and the action it
 * describes are committed atomically — a critical action never succeeds
 * without leaving a trace. The methods are meant to be called from the
 * existing service hooks (correction, exam, revision).
 *
 * <p><b>Concurrency caveat:</b> two parallel records may read the same
 * {@code prevHash} and fork the chain. This is acceptable for the load of this
 * system; perfect linearization would require DB-level serialization, which is
 * out of scope.
 */
@Service
@Transactional
public class AuditService {

	@Autowired
	private AuditLogRepository auditLogRepository;

	// -------------------------------------------------------------- record

	/** Full record, with before/after values (e.g. grade changes). */
	public void record(Person actor, AuditAction action, String targetType, Long targetId,
			String description, String prevValue, String newValue) {
		// Truncate to SECONDS so the timestamp used in the hash is exactly what
		// the DB stores and returns — Instant.now() has nanosecond precision but
		// the column stores at most microseconds, so hashing the full-precision
		// instant would make verifyChain() recompute a different hash. Second
		// resolution is ample for an audit log and is round-trip-stable.
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		String prevHash = auditLogRepository.findTopByOrderByIdDesc()
				.map(AuditLog::getEntryHash)
				.orElse(AuditLog.GENESIS_HASH);
		String entryHash = hash(payload(now, actorIdOf(actor), action, targetType, targetId,
				description, prevValue, newValue, prevHash));
		AuditLog log = new AuditLog(
				now,
				actorIdOf(actor),
				actor != null ? actor.getEmail() : null,
				actor != null ? actor.getName() : "Sistema",
				actor != null ? actor.getType().name() : "SYSTEM",
				action,
				targetType,
				targetId,
				description,
				prevValue,
				newValue,
				prevHash,
				entryHash);
		auditLogRepository.save(log);
	}

	/** Record without before/after values (the common case). */
	public void record(Person actor, AuditAction action, String targetType, Long targetId,
			String description) {
		record(actor, action, targetType, targetId, description, null, null);
	}

	/** System/scheduler action — no authenticated actor. */
	public void recordSystem(AuditAction action, String targetType, Long targetId,
			String description) {
		record(null, action, targetType, targetId, description, null, null);
	}

	// ---------------------------------------------------------------- list

	/** All entries, newest first (admin view). */
	@Transactional
	public List<AuditLogDto> list() {
		return auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
				.map(AuditLogDto::new)
				.toList();
	}

	// --------------------------------------------------------------- verify

	/**
	 * Recomputes the hash chain and reports whether it is intact. Iterates
	 * entries in id order: for each, recomputes {@code entryHash} from the
	 * stored fields and checks it equals the stored value, and checks that
	 * {@code prevHash} equals the previous entry's {@code entryHash} (the
	 * first must equal the genesis hash). Returns the id of the first entry
	 * that fails either check.
	 */
	@Transactional
	public IntegrityReportDto verifyChain() {
		List<AuditLog> entries = auditLogRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
		if (entries.isEmpty()) {
			return new IntegrityReportDto(true, null, 0L,
					"Cadeia vazia — sem entradas para verificar.");
		}
		String expectedPrev = AuditLog.GENESIS_HASH;
		for (AuditLog log : entries) {
			if (!log.getPrevHash().equals(expectedPrev)) {
				return new IntegrityReportDto(false, log.getId(), entries.size(),
						"Cadeia quebrada na entrada #" + log.getId()
								+ " — prevHash inconsistente com a entrada anterior.");
			}
			String recomputed = hash(payload(log.getOccurredAt(), log.getActorId(),
					AuditAction.valueOf(log.getAction()), log.getTargetType(), log.getTargetId(),
					log.getDescription(), log.getPrevValue(), log.getNewValue(),
					log.getPrevHash()));
			if (!recomputed.equals(log.getEntryHash())) {
				return new IntegrityReportDto(false, log.getId(), entries.size(),
						"Cadeia quebrada na entrada #" + log.getId()
								+ " — entrada adulterada (hash não corresponde ao conteúdo).");
			}
			expectedPrev = log.getEntryHash();
		}
		return new IntegrityReportDto(true, null, entries.size(),
				"Cadeia íntegra (" + entries.size() + " entradas verificadas).");
	}

	// ----------------------------------------------------------- internals

	private static Long actorIdOf(Person actor) {
		return actor != null ? actor.getId() : null;
	}

	/**
	 * Canonical, deterministic payload string. Nulls are normalized to the
	 * empty string so the hash is reproducible at verify time from the stored
	 * columns. The delimiter {@code |} is not expected in the controlled
	 * fields (ids, enum names, ISO instants); free-text fields (description,
	 * prev/new value) are hashed as-is — they are part of what tampering
	 * would change, which is exactly what we want to detect.
	 */
	static String payload(Instant occurredAt, Long actorId, AuditAction action,
			String targetType, Long targetId, String description,
			String prevValue, String newValue, String prevHash) {
		return n(occurredAt) + "|" + n(actorId) + "|" + n(action) + "|" + n(targetType)
				+ "|" + n(targetId) + "|" + n(description) + "|" + n(prevValue)
				+ "|" + n(newValue) + "|" + n(prevHash);
	}

	private static String n(Object o) {
		return o == null ? "" : String.valueOf(o);
	}

	static String hash(String payload) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is mandated by the Java spec — this is unreachable.
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}