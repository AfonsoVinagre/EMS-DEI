package pt.ulisboa.tecnico.rnl.dei.ems.audit.dto;

import java.time.Instant;

import pt.ulisboa.tecnico.rnl.dei.ems.audit.domain.AuditLog;

/**
 * Output DTO for an audit-log entry. Exposed only to administrators
 * ({@code AUDIT_READ}) via {@code GET /audit-logs}. The {@code prevHash} and
 * {@code entryHash} are included so the UI can show the chain is verifiable.
 */
public record AuditLogDto(
		long id,
		Instant occurredAt,
		Long actorId,
		String actorEmail,
		String actorName,
		String actorType,
		String action,
		String targetType,
		Long targetId,
		String description,
		String prevValue,
		String newValue,
		String prevHash,
		String entryHash) {

	public AuditLogDto(AuditLog log) {
		this(
				log.getId(),
				log.getOccurredAt(),
				log.getActorId(),
				log.getActorEmail(),
				log.getActorName(),
				log.getActorType(),
				log.getAction(),
				log.getTargetType(),
				log.getTargetId(),
				log.getDescription(),
				log.getPrevValue(),
				log.getNewValue(),
				log.getPrevHash(),
				log.getEntryHash());
	}
}